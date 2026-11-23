package com.ediary.backend.user.admin.service;

import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.schedule.repository.SchoolClassRepository;
import com.ediary.backend.schedule.repository.SubjectRepository;
import com.ediary.backend.user.admin.dto.AdminStudentOption;
import com.ediary.backend.user.admin.dto.CreateAdminUserRequest;
import com.ediary.backend.user.admin.dto.UserImportDtos.*;
import com.ediary.backend.user.admin.repository.UserImportLockRepository;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.repository.StudentProfileRepository;
import jakarta.validation.Validator;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserImportService {
    private final UserImportFileService files;
    private final AdminUserService users;
    private final Validator validator;
    private final SchoolClassRepository schoolClasses;
    private final SubjectRepository subjects;
    private final StudentProfileRepository students;
    private final UserImportLockRepository importLock;
    private final AuditService audit;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern CLASS = Pattern.compile("^(\\d{1,2})\\s*-\\s*([\\p{L}]{1,5})$");
    private static final Map<String,String> FIELDS = Map.ofEntries(Map.entry("role","Роль"),Map.entry("login","Логін"),
        Map.entry("lastName","Прізвище"),Map.entry("firstName","Ім’я"),Map.entry("patronymic","По батькові"),
        Map.entry("email","Електронна пошта"),Map.entry("phone","Телефон"),Map.entry("birthDate","Дата народження"));
    private static final String STUDENT_LOGIN = "Логін учня";
    private record PreparedRow(int number, CreateAdminUserRequest request, AdminStudentOption student) {}
    private record Checked(Preview preview, List<PreparedRow> requests) {}

    @Transactional
    public Preview preview(MultipartFile file, String format) {
        return check(read(file,format),format).preview();
    }

    @Transactional
    public Result confirm(MultipartFile file, String format, String fingerprint) {
        byte[] bytes = read(file,format);
        if (!digest(bytes).equals(fingerprint)) throw invalid("IMPORT_FILE_CHANGED", "Файл змінився. Перевірте його повторно.");
        // Hold the same database row until the whole import commits or rolls back.
        importLock.acquire().orElseThrow(() -> new IllegalStateException("Missing user import lock row"));
        Checked checked = check(bytes,format);
        if (checked.preview().invalid() != 0) throw invalid("IMPORT_VALIDATION_FAILED", "Дані не пройшли повторну перевірку. Жодного користувача не створено.");
        var ordered = checked.requests().stream()
            .sorted(Comparator.comparing(row -> row.request().role() == UserRole.PARENT))
            .toList();
        for (var row : ordered) {
            try {
                var request = row.request();
                if (row.student() != null) {
                    var student = row.student();
                    if (student.id() == null) {
                        student = users.findLinkableStudent(student.login())
                            .orElseThrow(() -> invalid("INVALID_LINKED_STUDENT", "Учня для прив’язки не знайдено"));
                    }
                    request = request.withStudentId(student.id());
                }
                users.create(request);
            }
            catch (ApplicationException error) {
                throw invalid("IMPORT_CREATION_FAILED", "Рядок " + row.number() + ": " + error.getMessage() + ". Імпорт скасовано повністю.");
            }
        }
        int count = checked.requests().size();
        audit.record("USERS_IMPORTED", "USER_IMPORT", UUID.randomUUID().toString(), of(), of("count",count,"created",true));
        return new Result(count, "Створено користувачів: " + count + ". Листи з даними для входу передано на надсилання.");
    }

    private byte[] read(MultipartFile file, String format) {
        if (!Set.of("csv","xlsx").contains(format)) throw invalid("INVALID_IMPORT_FORMAT","Оберіть CSV або XLSX.");
        if (file.getSize() > UserImportFileService.MAX_BYTES) throw invalid("IMPORT_TOO_LARGE","Розмір файлу не має перевищувати 5 МБ.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith("."+format))
            throw invalid("INVALID_IMPORT_FORMAT","Розширення файлу не відповідає вибраному формату.");
        try { return file.getBytes(); }
        catch (IOException error) { throw invalid("INVALID_IMPORT_FILE","Не вдалося прочитати файл."); }
    }

    private Checked check(byte[] bytes, String format) {
        List<RawRow> raw = files.parse(bytes,format);
        List<CreateAdminUserRequest> requests = new ArrayList<>();
        List<Map<String,String>> errors = new ArrayList<>();
        Map<String,List<Integer>> logins = new HashMap<>(), emails = new HashMap<>();
        for (int index = 0; index < raw.size(); index++) {
            var values = raw.get(index).values();
            var problems = new LinkedHashMap<String,String>(); errors.add(problems);
            UserRole role = switch (values.get(0).toUpperCase(Locale.ROOT)) {
                case "УЧЕНЬ","STUDENT" -> UserRole.STUDENT;
                case "ВЧИТЕЛЬ","TEACHER" -> UserRole.TEACHER;
                case "БАТЬКИ","PARENT" -> UserRole.PARENT;
                default -> null;
            };
            if (role == null) problems.put("Роль","Оберіть Учень, Вчитель або Батьки. Адміністраторів імпортувати не можна.");
            LocalDate birth = null;
            try { birth = LocalDate.parse(values.get(7),DATE); }
            catch (DateTimeParseException error) { problems.put("Дата народження","Вкажіть дійсну дату у форматі дд.мм.рррр."); }
            Long classId = null, subjectId = null;
            if (role == UserRole.STUDENT) {
                var match = CLASS.matcher(values.get(8));
                if (!match.matches()) problems.put("Клас","Вкажіть клас, наприклад 9-А.");
                else {
                    var matches = schoolClasses.findIdsByYearAndNumberAndLetter(
                        values.get(9), Integer.parseInt(match.group(1)), match.group(2));
                    if (matches.size()!=1) problems.put("Клас","Клас для вказаного навчального року не знайдено."); else classId=matches.getFirst();
                }
                if (!values.get(10).isBlank()) problems.put("Предмет","Для учня залиште предмет порожнім.");
            } else if (!values.get(8).isBlank() || !values.get(9).isBlank()) problems.put("Клас","Клас і навчальний рік заповнюються лише для учнів.");
            if (role == UserRole.TEACHER) {
                var matches = subjects.findIdsByTitleIgnoreCase(values.get(10));
                if (matches.size()!=1) problems.put("Предмет","Вкажіть назву наявного предмета."); else subjectId=matches.getFirst();
            } else if (role != UserRole.STUDENT && !values.get(10).isBlank()) problems.put("Предмет","Предмет заповнюється лише для вчителя.");
            String login = values.get(1).toLowerCase(Locale.ROOT), email = values.get(5).toLowerCase(Locale.ROOT);
            logins.computeIfAbsent(login,key->new ArrayList<>()).add(index);
            emails.computeIfAbsent(email,key->new ArrayList<>()).add(index);
            if (role == UserRole.PARENT && values.get(11).isBlank()) {
                problems.put(STUDENT_LOGIN,"Вкажіть логін учня для прив’язки.");
            } else if (role != UserRole.PARENT && !values.get(11).isBlank()) {
                problems.put(STUDENT_LOGIN,"Логін учня заповнюється лише для батьків.");
            }
            var input = new CreateAdminUserRequest(login,values.get(3),values.get(2),values.get(4),email,values.get(6),birth,role,classId,subjectId,null);
            requests.add(input);
            validator.validate(input).stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).forEach(v->
                problems.putIfAbsent(FIELDS.getOrDefault(v.getPropertyPath().toString(),v.getPropertyPath().toString()),v.getMessage()));
        }
        logins.values().stream().filter(items->items.size()>1).forEach(items->items.forEach(index->errors.get(index).put("Логін","Повторюється у файлі.")));
        emails.values().stream().filter(items->items.size()>1).forEach(items->items.forEach(index->errors.get(index).put("Електронна пошта","Повторюється у файлі.")));
        Map<Long,List<Integer>> classes = new TreeMap<>();
        for (int index=0;index<requests.size();index++) if (requests.get(index).classId()!=null && errors.get(index).isEmpty())
            classes.computeIfAbsent(requests.get(index).classId(),id->new ArrayList<>()).add(index);
        classes.forEach((id,items)->{
            var schoolClass = schoolClasses.findByIdForUpdate(id);
            if (schoolClass.isEmpty()) {
                items.forEach(index -> errors.get(index).put("Клас", "Клас для вказаного навчального року не знайдено."));
            } else if (items.size() > schoolClass.get().getLimit() - students.countBySchoolClassId(id)) {
                items.forEach(index -> errors.get(index).put("Клас", "Недостатньо вільних місць для всіх учнів із файлу."));
            }
        });
        Map<String,AdminStudentOption> pendingStudents = new HashMap<>();
        for (int index=0;index<requests.size();index++) if (errors.get(index).isEmpty()) {
            var input = requests.get(index);
            try {
                var targets = users.validateCreation(input);
                if (input.role() == UserRole.STUDENT) {
                    String fullName = String.join(" ", input.lastName(), input.firstName(), input.patronymic()).strip();
                    pendingStudents.put(input.login(), new AdminStudentOption(null, input.login(), fullName, targets.schoolClass().getDisplayName()));
                }
            }
            catch (ApplicationException error) {
                String field = switch (error.getCode()) {
                    case "LOGIN_ALREADY_EXISTS" -> "Логін";
                    case "EMAIL_ALREADY_REGISTERED" -> "Електронна пошта";
                    case "INVALID_PHONE" -> "Телефон";
                    case "INVALID_BIRTH_DATE", "ADULT_ROLE_REQUIRES_ADULT_AGE" -> "Дата народження";
                    case "CLASS_ACADEMIC_YEAR_MISMATCH" -> "Навчальний рік";
                    case "CLASS_NOT_FOUND", "CLASS_TEACHER_REQUIRED", "SCHOOL_CLASS_IS_FULL" -> "Клас";
                    case "SUBJECT_NOT_FOUND" -> "Предмет";
                    default -> "Роль";
                };
                errors.get(index).put(field,error.getMessage());
            }
        }
        List<Row> rows = new ArrayList<>();
        List<PreparedRow> prepared = new ArrayList<>();
        for (int index=0;index<raw.size();index++) {
            var row = raw.get(index);
            var problems = errors.get(index);
            var request = requests.get(index);
            AdminStudentOption student = null;
            if (request.role() == UserRole.PARENT && !problems.containsKey(STUDENT_LOGIN)) {
                String childLogin = row.values().get(11).toLowerCase(Locale.ROOT);
                student = pendingStudents.get(childLogin);
                if (student == null) {
                    if (logins.containsKey(childLogin)) {
                        problems.put(STUDENT_LOGIN,"Логін має належати коректному рядку учня у файлі.");
                    } else {
                        student = users.findLinkableStudent(childLogin).orElse(null);
                        if (student == null) problems.put(STUDENT_LOGIN,"Активного учня з таким логіном не знайдено.");
                    }
                }
            }
            var linkedStudent = student == null ? null : new LinkedStudent(student.fullName(), student.className());
            rows.add(new Row(row.number(),row.values(),problems,linkedStudent));
            if (problems.isEmpty()) prepared.add(new PreparedRow(row.number(),request,student));
        }
        int valid = prepared.size();
        return new Checked(new Preview(digest(bytes),UserImportFileService.COLUMNS,rows,rows.size(),valid,rows.size()-valid),prepared);
    }
    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private ApplicationException invalid(String code,String message) { return new ApplicationException(HttpStatus.CONFLICT,code,message); }
}
