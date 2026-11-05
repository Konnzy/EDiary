package com.ediary.backend.user.admin.service;

import com.ediary.backend.common.audit.AuditService;
import static com.ediary.backend.common.audit.AuditValues.of;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.admin.dto.CreateAdminUserRequest;
import com.ediary.backend.user.admin.dto.UserImportDtos.*;
import com.ediary.backend.user.entity.UserRole;
import jakarta.validation.Validator;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserImportService {
    private final UserImportFileService files;
    private final AdminUserService users;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern CLASS = Pattern.compile("^(\\d{1,2})\\s*-\\s*([\\p{L}]{1,5})$");
    private static final Map<String,String> FIELDS = Map.ofEntries(Map.entry("role","Роль"),Map.entry("login","Логін"),
        Map.entry("lastName","Прізвище"),Map.entry("firstName","Ім’я"),Map.entry("patronymic","По батькові"),
        Map.entry("email","Електронна пошта"),Map.entry("phone","Телефон"),Map.entry("birthDate","Дата народження"),
        Map.entry("parentLinkCode","Код прив’язки"));
    private record Checked(Preview preview, List<CreateAdminUserRequest> requests) {}

    @Transactional
    public Preview preview(MultipartFile file, String format) {
        return check(read(file,format),format).preview();
    }

    @Transactional
    public Result confirm(MultipartFile file, String format, String fingerprint) {
        byte[] bytes = read(file,format);
        if (!digest(bytes).equals(fingerprint)) throw invalid("IMPORT_FILE_CHANGED", "Файл змінився. Перевірте його повторно.");
        // Serialize competing imports without retaining uploaded files or adding history tables.
        jdbc.execute("select pg_advisory_xact_lock(15342941)");
        Checked checked = check(bytes,format);
        if (checked.preview().invalid() != 0) throw invalid("IMPORT_VALIDATION_FAILED", "Дані не пройшли повторну перевірку. Жодного користувача не створено.");
        for (int index = 0; index < checked.requests().size(); index++) {
            try { users.create(checked.requests().get(index)); }
            catch (ApplicationException error) {
                throw invalid("IMPORT_CREATION_FAILED", "Рядок " + checked.preview().rows().get(index).number() + ": " + error.getMessage() + ". Імпорт скасовано повністю.");
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
                    var ids = jdbc.queryForList("select class_id from school_class where class_number=? and upper(class_letter)=upper(?) and class_academic_year=?",
                        Long.class,Integer.parseInt(match.group(1)),match.group(2),values.get(9));
                    if (ids.size()!=1) problems.put("Клас","Клас для вказаного навчального року не знайдено."); else classId=ids.getFirst();
                }
                if (!values.get(10).isBlank()) problems.put("Предмет","Для учня залиште предмет порожнім.");
            } else if (!values.get(8).isBlank() || !values.get(9).isBlank()) problems.put("Клас","Клас і навчальний рік заповнюються лише для учнів.");
            if (role == UserRole.TEACHER) {
                var ids = jdbc.queryForList("select subject_id from subject where lower(subject_title)=lower(?)",Long.class,values.get(10));
                if (ids.size()!=1) problems.put("Предмет","Вкажіть назву наявного предмета."); else subjectId=ids.getFirst();
            } else if (role != UserRole.STUDENT && !values.get(10).isBlank()) problems.put("Предмет","Предмет заповнюється лише для вчителя.");
            String login = values.get(1).toLowerCase(Locale.ROOT), email = values.get(5).toLowerCase(Locale.ROOT);
            logins.computeIfAbsent(login,key->new ArrayList<>()).add(index);
            emails.computeIfAbsent(email,key->new ArrayList<>()).add(index);
            var input = new CreateAdminUserRequest(login,values.get(3),values.get(2),values.get(4),email,values.get(6),birth,role,classId,subjectId,values.get(11).toUpperCase(Locale.ROOT));
            requests.add(input);
            validator.validate(input).stream().sorted(Comparator.comparing(v->v.getPropertyPath().toString())).forEach(v->
                problems.putIfAbsent(FIELDS.getOrDefault(v.getPropertyPath().toString(),v.getPropertyPath().toString()),v.getMessage()));
        }
        logins.values().stream().filter(items->items.size()>1).forEach(items->items.forEach(index->errors.get(index).put("Логін","Повторюється у файлі.")));
        emails.values().stream().filter(items->items.size()>1).forEach(items->items.forEach(index->errors.get(index).put("Електронна пошта","Повторюється у файлі.")));
        Map<Long,List<Integer>> classes = new TreeMap<>();
        for (int index=0;index<requests.size();index++) if (requests.get(index).classId()!=null)
            classes.computeIfAbsent(requests.get(index).classId(),id->new ArrayList<>()).add(index);
        classes.forEach((id,items)->{
            jdbc.queryForObject("select class_id from school_class where class_id=? for update",Long.class,id);
            Long free = jdbc.queryForObject("select class_limit-(select count(*) from student where class_id=?) from school_class where class_id=?",Long.class,id,id);
            if (free == null || items.size()>free) items.forEach(index->errors.get(index).put("Клас","Недостатньо вільних місць для всіх учнів із файлу."));
        });
        for (int index=0;index<requests.size();index++) if (errors.get(index).isEmpty()) {
            try { users.validateCreation(requests.get(index)); }
            catch (ApplicationException error) {
                String field = switch (error.getCode()) {
                    case "LOGIN_ALREADY_EXISTS" -> "Логін";
                    case "EMAIL_ALREADY_REGISTERED" -> "Електронна пошта";
                    case "INVALID_PHONE" -> "Телефон";
                    case "INVALID_BIRTH_DATE", "ADULT_ROLE_REQUIRES_ADULT_AGE" -> "Дата народження";
                    case "INVALID_PARENT_LINK_CODE" -> "Код прив’язки";
                    case "CLASS_ACADEMIC_YEAR_MISMATCH" -> "Навчальний рік";
                    case "CLASS_NOT_FOUND", "CLASS_TEACHER_REQUIRED", "SCHOOL_CLASS_IS_FULL" -> "Клас";
                    case "SUBJECT_NOT_FOUND" -> "Предмет";
                    default -> "Роль";
                };
                errors.get(index).put(field,error.getMessage());
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int index=0;index<raw.size();index++) {
            var values = new ArrayList<>(raw.get(index).values());
            if (!values.get(11).isEmpty()) values.set(11,"••••-••••");
            rows.add(new Row(raw.get(index).number(),values,errors.get(index)));
        }
        int valid = (int)errors.stream().filter(Map::isEmpty).count();
        return new Checked(new Preview(digest(bytes),UserImportFileService.COLUMNS,rows,rows.size(),valid,rows.size()-valid),requests);
    }
    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private ApplicationException invalid(String code,String message) { return new ApplicationException(HttpStatus.CONFLICT,code,message); }
}
