package com.ediary.backend.schedule.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.common.importfile.TabularImportFile;
import com.ediary.backend.schedule.dto.request.SaveScheduleLessonRequest;
import com.ediary.backend.schedule.dto.response.ScheduleImportDtos.*;
import com.ediary.backend.schedule.dto.response.ScheduleLessonResponse;
import com.ediary.backend.schedule.entity.ScheduleEntry;
import com.ediary.backend.schedule.entity.Weekday;
import com.ediary.backend.schedule.repository.ScheduleRepository;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.user.entity.UserStatus;
import com.ediary.backend.user.repository.TeacherProfileRepository;
import jakarta.validation.Validator;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ScheduleImportService {
    private static final List<String> COLUMNS = List.of("Тиждень", "День", "Номер уроку", "Предмет", "Логін учителя", "Кабінет");
    private static final TabularImportFile FILE = new TabularImportFile(COLUMNS, 80,
        "Максимум 80 уроків за один імпорт.", "Додайте хоча б один урок після заголовків.");
    private static final Map<String, Weekday> DAYS = Map.of(
        "понеділок", Weekday.MONDAY, "вівторок", Weekday.TUESDAY, "середа", Weekday.WEDNESDAY,
        "четвер", Weekday.THURSDAY, "п'ятниця", Weekday.FRIDAY);

    private final ScheduleRepository schedules;
    private final SubjectRepository subjects;
    private final TeacherProfileRepository teachers;
    private final AdminScheduleService scheduleService;
    private final Validator validator;
    private final ObjectMapper json;

    private record Slot(int week, Weekday day, int lesson) {}
    private record TeacherSlot(Slot slot, long teacherId) {}
    private record Input(TabularImportFile.RawRow row, Slot slot, Map<Column, List<String>> errors) {}
    private record CheckedRow(Row view, Slot slot, SaveScheduleLessonRequest request) {}
    private record Snapshot(Long classId, String year, String format, List<ScheduleLessonResponse> firstWeek,
                            List<ScheduleLessonResponse> secondWeek, List<Row> rows) {}
    private record Check(Preview preview, List<CheckedRow> rows) {}

    public byte[] template(String format) {
        validateFormat(format);
        return FILE.template(format, "Розклад", List.of(
            List.of("1", "Понеділок", "1", "Математика", "teacher.example", "101"),
            List.of("2", "П’ятниця", "3", "Українська мова", "teacher.language", "205")
        ));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Preview preview(Long classId, String year, MultipartFile file, String format) {
        scheduleService.requireEditableClass(classId, year, false);
        return check(classId, year, read(file, format), format).preview();
    }

    @Transactional
    public Result confirm(Long classId, String year, MultipartFile file, String format, String fingerprint,
                          List<Integer> keepRows, List<Integer> replaceRows) {
        byte[] bytes = read(file, format);
        var schoolClass = scheduleService.requireEditableClass(classId, year, true);
        var check = check(classId, year, bytes, format);
        if (!check.preview().fingerprint().equals(fingerprint)) {
            throw conflict("SCHEDULE_IMPORT_STALE", "Файл або розклад змінився. Перевірте попередній перегляд повторно.");
        }
        if (check.rows().stream().anyMatch(row -> row.view().status() == Status.INVALID)) {
            throw conflict("SCHEDULE_IMPORT_INVALID", "Виправте помилки у файлі перед імпортом.");
        }

        Set<Integer> keep = new HashSet<>(keepRows);
        Set<Integer> replace = new HashSet<>(replaceRows);
        Set<Integer> selected = new HashSet<>(keep);
        selected.addAll(replace);
        Set<Integer> conflicts = check.rows().stream().map(CheckedRow::view)
            .filter(row -> row.status() == Status.CONFLICT).map(Row::number).collect(Collectors.toSet());
        if (!selected.equals(conflicts) || selected.size() != keepRows.size() + replaceRows.size()) {
            throw conflict("SCHEDULE_IMPORT_DECISIONS_REQUIRED", "Оберіть, що зробити з кожним зайнятим слотом.");
        }

        int added = 0;
        int unchanged = 0;
        Map<Integer, List<SaveScheduleLessonRequest>> changes = new TreeMap<>();
        for (var row : check.rows()) {
            if (row.view().status() == Status.UNCHANGED) unchanged++;
            if (row.view().status() == Status.ADD) added++;
            if (row.view().status() == Status.ADD || replace.contains(row.view().number())) {
                changes.computeIfAbsent(row.slot().week(), ignored -> new ArrayList<>()).add(row.request());
            }
        }
        try {
            changes.forEach((week, lessons) -> scheduleService.mergeImportedLessons(schoolClass, year, week, lessons));
        } catch (DataIntegrityViolationException error) {
            String detail = error.getMostSpecificCause().getMessage();
            if (detail != null && (detail.contains("uq_active_teacher_schedule") || detail.contains("uq_active_class_schedule"))) {
                throw conflict("SCHEDULE_IMPORT_STALE", "Розклад змінився під час імпорту. Перевірте файл повторно.");
            }
            throw error;
        }
        return new Result(added, replace.size(), keep.size(), unchanged);
    }

    private Check check(Long classId, String year, byte[] bytes, String format) {
        var inputs = FILE.parse(bytes, format).stream().map(this::parseSlot).toList();
        var subjectsByTitle = subjects.findAllByNormalizedTitles(keys(inputs, 3)).stream()
            .collect(Collectors.toMap(subject -> key(subject.getTitle()), Function.identity()));
        var teachersByLogin = teachers.findAllByNormalizedLogins(keys(inputs, 4)).stream()
            .collect(Collectors.toMap(teacher -> key(teacher.getUser().getLogin()), Function.identity()));
        Map<Slot, ScheduleEntry> current = new HashMap<>();
        Set<TeacherSlot> occupied = new HashSet<>();
        List<List<ScheduleLessonResponse>> baseline = new ArrayList<>(2);
        for (int week : List.of(1, 2)) {
            var entries = schedules.findAllBySchoolClass_IdAndAcademicYearAndWeekNumberAndActiveTrueOrderByWeekdayAscLessonNumberAsc(
                classId, year, week);
            entries.forEach(entry -> current.put(slot(entry), entry));
            baseline.add(entries.stream().map(scheduleService::toLessonResponse).toList());
            schedules.findTeacherConflicts(classId, year, week).forEach(entry ->
                occupied.add(new TeacherSlot(slot(entry), entry.getTeacher().getId())));
        }
        var slotCounts = inputs.stream().map(Input::slot).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<CheckedRow> checked = new ArrayList<>(inputs.size());
        for (var input : inputs) {
            var values = input.row().values();
            var errors = input.errors();
            var slot = input.slot();
            if (slot != null && slotCounts.get(slot) > 1) addError(errors, Column.LESSON, "Слот повторюється у файлі.");
            var subject = subjectsByTitle.get(key(values.get(3)));
            var teacher = teachersByLogin.get(key(values.get(4)));
            if (subject == null) addError(errors, Column.SUBJECT, "Предмет не знайдено. Вкажіть його назву з довідника.");
            if (teacher == null) addError(errors, Column.TEACHER, "Учителя з таким логіном не знайдено.");
            else if (teacher.getUser().getStatus() != UserStatus.ACTIVE) addError(errors, Column.TEACHER, "Акаунт учителя неактивний.");
            else if (subject != null && teacher.getSubjects().stream().noneMatch(item -> item.getId().equals(subject.getId()))) {
                addError(errors, Column.TEACHER, "Учитель не призначений до цього предмета.");
            }
            var existing = current.get(slot);
            var proposed = new SaveScheduleLessonRequest(slot == null ? null : slot.day(),
                slot == null ? null : slot.lesson(), subject == null ? null : subject.getId(),
                teacher == null ? null : teacher.getId(), values.get(5));
            validator.validateProperty(proposed, "room").stream().map(error -> error.getMessage())
                .sorted().findFirst().ifPresent(error -> addError(errors, Column.ROOM, error));
            if (slot != null && teacher != null && occupied.contains(new TeacherSlot(slot, teacher.getId()))) {
                addError(errors, Column.TEACHER, "Учитель уже має урок в іншому класі в цей час.");
            }
            var viewBefore = existing == null ? null : scheduleService.toLessonResponse(existing);
            if (!errors.isEmpty()) {
                checked.add(new CheckedRow(new Row(input.row().number(), values, Status.INVALID, viewBefore, null, errors), slot, null));
                continue;
            }
            proposed = new SaveScheduleLessonRequest(slot.day(), slot.lesson(), subject.getId(), teacher.getId(),
                AdminScheduleService.normalizeRoom(proposed.room()));
            Status status = existing == null ? Status.ADD
                : scheduleService.sameDefinition(existing, proposed) ? Status.UNCHANGED : Status.CONFLICT;
            var viewAfter = scheduleService.describeImportedLesson(proposed, subject, teacher);
            checked.add(new CheckedRow(new Row(input.row().number(), values, status, viewBefore, viewAfter, Map.of()), slot, proposed));
        }
        var rows = checked.stream().map(CheckedRow::view).toList();
        String fingerprint = fingerprint(bytes, new Snapshot(classId, year, format, baseline.getFirst(), baseline.getLast(), rows));
        return new Check(new Preview(fingerprint, COLUMNS, rows), checked);
    }

    private Input parseSlot(TabularImportFile.RawRow row) {
        var values = row.values();
        Map<Column, List<String>> errors = new EnumMap<>(Column.class);
        String week = values.get(0);
        Weekday day = DAYS.get(key(values.get(1)).replace('’', '\'').replace('ʼ', '\''));
        String lesson = values.get(2);
        if (!week.matches("[12]")) addError(errors, Column.WEEK, "Тиждень має бути 1 або 2.");
        if (day == null) addError(errors, Column.DAY, "Вкажіть день з понеділка до п’ятниці.");
        if (!lesson.matches("[1-8]")) addError(errors, Column.LESSON, "Номер уроку має бути від 1 до 8.");
        var slot = errors.isEmpty() ? new Slot(Integer.parseInt(week), day, Integer.parseInt(lesson)) : null;
        return new Input(row, slot, errors);
    }

    private static void addError(Map<Column, List<String>> errors, Column column, String reason) {
        errors.computeIfAbsent(column, ignored -> new ArrayList<>()).add(reason);
    }

    private Slot slot(ScheduleEntry entry) {
        return new Slot(entry.getWeekNumber(), entry.getWeekday(), entry.getLessonNumber());
    }

    private static Set<String> keys(List<Input> inputs, int column) {
        return inputs.stream().map(input -> key(input.row().values().get(column))).collect(Collectors.toSet());
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String fingerprint(byte[] bytes, Snapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Hash the file separately so its boundary with the canonical snapshot is fixed.
            byte[] fileHash = digest.digest(bytes);
            digest.update(fileHash);
            return HexFormat.of().formatHex(digest.digest(json.writeValueAsBytes(snapshot)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private byte[] read(MultipartFile file, String format) {
        validateFormat(format);
        if (file.getSize() > TabularImportFile.MAX_BYTES) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "IMPORT_TOO_LARGE", "Файл не має перевищувати 5 МБ.");
        }
        if (file.getOriginalFilename() == null || !key(file.getOriginalFilename()).endsWith("." + format)) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_IMPORT_FORMAT", "Розширення файлу не відповідає формату.");
        }
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_IMPORT_FILE", "Не вдалося прочитати файл.");
        }
    }

    private static void validateFormat(String format) {
        if (!"csv".equals(format) && !"xlsx".equals(format)) {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "INVALID_IMPORT_FORMAT", "Оберіть CSV або XLSX.");
        }
    }

    private static ApplicationException conflict(String code, String message) {
        return new ApplicationException(HttpStatus.CONFLICT, code, message);
    }
}
