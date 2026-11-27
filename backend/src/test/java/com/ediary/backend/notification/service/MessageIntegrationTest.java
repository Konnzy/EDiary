package com.ediary.backend.notification.service;

import com.ediary.backend.notification.dto.MessageDtos;
import com.ediary.backend.notification.repository.MessageRepository;
import com.ediary.backend.schedule.dto.response.ScheduleImportDtos.Status;
import com.ediary.backend.schedule.dto.response.ScheduleImportDtos.Column;
import com.ediary.backend.schedule.service.ScheduleImportService;
import com.ediary.backend.user.repository.StudentProfileRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"app.mail.enabled=false","app.bootstrap-admin.enabled=false","app.notifications.scheduling-enabled=false"})
@EnabledIfEnvironmentVariable(named="EDIARY_RUN_DB_TESTS",matches="true")
class MessageIntegrationTest {
    private static final String SCHEMA="message_test_"+UUID.randomUUID().toString().replace("-","");
    private static final String URL=System.getenv().getOrDefault("EDIARY_DB_URL","jdbc:postgresql://localhost:5432/ediary");
    private static final String USER=System.getenv().getOrDefault("EDIARY_DB_USERNAME","ediary");
    private static final String PASSWORD=System.getenv().getOrDefault("EDIARY_DB_PASSWORD","ediary");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->URL+(URL.contains("?")?"&":"?")+"currentSchema="+SCHEMA);
        p.add("spring.flyway.schemas",()->SCHEMA);p.add("spring.flyway.default-schema",()->SCHEMA);
        p.add("spring.jpa.properties.hibernate.default_schema",()->SCHEMA);
    }
    @AfterAll static void cleanup() throws Exception {
        if(!SCHEMA.matches("message_test_[a-f0-9]{32}"))throw new IllegalStateException("Invalid test schema");
        try(var connection=DriverManager.getConnection(URL,USER,PASSWORD);var statement=connection.createStatement()) { statement.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE"); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MessageService messages;
    @Autowired MessageRepository repository;
    @Autowired com.ediary.backend.journal.service.AssessmentPolicyService assessment;
    @Autowired com.ediary.backend.journal.service.JournalService journal;
    @Autowired com.ediary.backend.journal.service.SubjectProgressService progress;
    @Autowired UserNotificationService notifications;
    @Autowired NotificationEmailSender sender;
    @Autowired StudentProfileRepository students;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext context;
    @Autowired com.ediary.backend.registration.service.AdminNotificationService adminNotifications;
    @Autowired com.ediary.backend.user.admin.service.UserImportService userImports;
    @Autowired com.ediary.backend.user.admin.service.UserImportFileService importFiles;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ScheduleImportService scheduleImports;
    @MockitoBean EmailService email;
    @MockitoBean Clock clock;
    private String prefix;
    private long studentUser,teacherUser,parentUser,studentId,outsider;
    private MockMvc mvc;
    @BeforeEach void setup() {
        reset(email);when(clock.instant()).thenReturn(Instant.parse("2026-10-20T10:00:00Z"));when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        prefix=UUID.randomUUID().toString().substring(0,8);
        studentUser=userId("student","STUDENT"); teacherUser=userId("teacher","TEACHER");parentUser=userId("parent","PARENT");outsider=userId("outsider","STUDENT");
        long teacher=id("insert into teacher(user_id,teacher_name,teacher_surname,teacher_birthdate,teacher_email,teacher_phone) values (?,'Олександр','Коваленко','1980-01-01',?,'000') returning teacher_id",teacherUser,prefix+"teacher@example.invalid");
        int classIndex=jdbc.queryForObject("select count(*) from school_class",Integer.class);
        long schoolClass=id("insert into school_class(class_number,class_letter,class_academic_year,class_teacher_id) values (?,?,'2026/2027',?) returning class_id",classIndex%12+1,String.valueOf("АБВГҐД".charAt(classIndex/12)),teacher);
        studentId=id("insert into student(user_id,class_id,student_name,student_surname,student_birthdate,student_enrollment_date,student_status) values (?,?,'Іван','Петренко','2010-01-01','2026-09-01','STUDYING') returning student_id",studentUser,schoolClass);
        long parent=id("insert into parent(user_id,parent_name,parent_surname,parent_birthdate,parent_email,parent_phone) values (?,'Олена','Петренко','1980-01-01',?,'000') returning parent_id",parentUser,prefix+"parent@example.invalid");
        jdbc.update("insert into student_parents(student_id,parent_id,is_primary_contact) values (?,?,true)",studentId,parent);
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }
    @Test void permissionsSearchIdempotencyReadImportantAndNotificationLinks() throws Exception {
        assertThat(messages.recipients(login("student"),"Ков Олек",null)).extracting(MessageDtos.Recipient::id).containsExactly(teacherUser);
        assertThat(messages.recipients(login("parent"),"",null)).extracting(MessageDtos.Recipient::id).containsExactly(teacherUser);
        assertThat(messages.recipients(login("teacher"),"Пет",null)).extracting(MessageDtos.Recipient::id).contains(studentUser,parentUser).doesNotContain(outsider);
        var request=new MessageDtos.Send(studentUser,"Перевірка","Звичайний текст <script>не виконується</script>",UUID.randomUUID());
        var sent=messages.send(login("teacher"),request);
        assertThat(messages.send(login("teacher"),request).id()).isEqualTo(sent.id());
        assertThat(messages.list(login("teacher"),"sent",0).messages().totalElements()).isEqualTo(1);
        assertThat(messages.list(login("student"),"inbox",0).unreadCount()).isEqualTo(1);
        assertThat(notifications.feed(login("student")).items()).anySatisfy(item->assertThat(item.target()).isEqualTo("student/messages?messageId="+sent.id()));
        assertThatThrownBy(()->messages.open(login("outsider"),sent.id())).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        assertThatThrownBy(()->messages.important(login("teacher"),sent.id(),true)).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        assertThat(messages.open(login("teacher"),sent.id()).read()).isFalse();
        assertThat(messages.open(login("student"),sent.id()).read()).isTrue();
        assertThat(notifications.feed(login("student")).unreadCount()).isZero();
        messages.important(login("student"),sent.id(),true);
        assertThat(messages.list(login("student"),"important",0).messages().totalElements()).isEqualTo(1);
        messages.important(login("student"),sent.id(),false);
        assertThat(messages.list(login("student"),"important",0).messages().totalElements()).isZero();
        mvc.perform(post("/api/messages").with(user(login("teacher")).roles("TEACHER")).with(csrf()).contentType("application/json")
            .content("{\"recipientId\":"+studentUser+",\"subject\":\"API\",\"text\":\"Текст\",\"clientRequestId\":\""+UUID.randomUUID()+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("API"));
        jdbc.update("update school_class set class_teacher_id=null where class_id=(select class_id from student where student_id=?)",studentId);
        assertThat(messages.open(login("student"),sent.id()).id()).isEqualTo(sent.id());
        assertThatThrownBy(()->messages.send(login("student"),new MessageDtos.Send(teacherUser,"Нове","Текст",UUID.randomUUID()))).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        mvc.perform(get("/api/messages")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/messages").with(user(login("teacher")).roles("TEACHER")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/messages").with(user(login("teacher")).roles("TEACHER")).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/messages/"+sent.id()+"/open").with(user(login("outsider")).roles("STUDENT")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void ordinaryEmailsAreOptInDurableDeduplicatedAndRespectOptOutBeforeDelivery() {
        when(email.isEnabled()).thenReturn(true);when(email.sendText(anyString(),anyString(),anyString())).thenReturn(true);
        jdbc.update("insert into user_notification_settings(user_id,grade_email_enabled,homework_email_enabled,homework_deadline_reminder_email_enabled,schedule_change_email_enabled,message_email_enabled) values (?,true,true,true,true,true)",studentUser);
        var tx=new TransactionTemplate(transactions);
        for(String category:List.of("GRADE","HOMEWORK","HOMEWORK_DEADLINE","SCHEDULE")) {
            tx.executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),category,"Тест","Подія",prefix+category));
            tx.executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),category,"Тест","Подія",prefix+category));
        }
        var notice=messages.send(login("teacher"),new MessageDtos.Send(studentUser,"Лист","Текст",UUID.randomUUID()));
        var ids=jdbc.queryForList("select notification_id from user_notification where recipient_id=?",Long.class,studentUser);
        assertThat(ids).hasSize(5);
        for(long id:ids){sender.deliver(id);sender.deliver(id);}
        verify(email,times(5)).sendText(anyString(),anyString(),contains("/student/"));
        tx.executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),"GRADE","Тест","Друга подія",prefix+"second"));
        jdbc.update("update user_notification_settings set grade_email_enabled=false where user_id=?",studentUser);
        long queued=id("select notification_id from user_notification where recipient_id=? and event_key=?",studentUser,prefix+"second");
        sender.deliver(queued);
        verify(email,times(5)).sendText(anyString(),anyString(),anyString());
        assertThat(jdbc.queryForObject("select email_due_at from user_notification where notification_id=?",LocalDateTime.class,queued)).isNull();
        assertThat(messages.list(login("student"),"inbox",0).messages().content().getFirst().id()).isEqualTo(notice.id());
    }
    @Test void transactionRollbackDoesNotPublishMailOrMessage() {
        long before=repository.count();
        new TransactionTemplate(transactions).executeWithoutResult(s->{messages.send(login("teacher"),new MessageDtos.Send(studentUser,"Відкат","Текст",UUID.randomUUID()));s.setRollbackOnly();});
        assertThat(repository.count()).isEqualTo(before);
        assertThat(notifications.feed(login("student")).items()).isEmpty();
    }
    @Test void emailUsesFullSnapshotWhileInAppPreviewRemainsBounded() {
        when(email.isEnabled()).thenReturn(true);when(email.sendText(anyString(),anyString(),anyString())).thenReturn(true);
        jdbc.update("insert into user_notification_settings(user_id,homework_email_enabled) values (?,true)",studentUser);
        String fullText="Предмет: Інформатика\nТермін виконання: 04.11.2026\nЗавдання:\n"+"А".repeat(1200);
        new TransactionTemplate(transactions).executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),"HOMEWORK","Домашнє завдання оновлено",fullText,prefix+"long"));
        long notice=id("select notification_id from user_notification where recipient_id=? and event_key=?",studentUser,prefix+"long");
        assertThat(jdbc.queryForObject("select length(body) from user_notification where notification_id=?",Integer.class,notice)).isLessThanOrEqualTo(500);
        assertThat(jdbc.queryForObject("select email_body from user_notification where notification_id=?",String.class,notice)).contains(fullText,"Учень:","Клас:");
        sender.deliver(notice);
        verify(email).sendText(anyString(),eq("Домашнє завдання оновлено"),argThat(body->!body.contains(fullText)&&body.contains("04.11.2026")&&body.contains("Переглянути домашнє завдання:")&&!body.contains("Дата події:")&&body.length()<600));
    }
    @Test void concurrentRetryCreatesOneMessageAndMailboxHasStablePagination() throws Exception {
        var request=new MessageDtos.Send(studentUser,"Повтор","Текст",UUID.randomUUID());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=executor.submit(()->{start.await();return messages.send(login("teacher"),request);});
            var second=executor.submit(()->{start.await();return messages.send(login("teacher"),request);});
            start.countDown();
            assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS).id()).isEqualTo(second.get(20,java.util.concurrent.TimeUnit.SECONDS).id());
        }
        assertThat(messages.list(login("teacher"),"sent",0).messages().totalElements()).isEqualTo(1);
        assertThatThrownBy(()->messages.send(login("teacher"),new MessageDtos.Send(studentUser,"Інший текст","Текст",request.clientRequestId())))
            .isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        for(int index=0;index<21;index++)messages.send(login("teacher"),new MessageDtos.Send(studentUser,"Лист "+index,"Текст",UUID.randomUUID()));
        var first=messages.list(login("student"),"inbox",0);
        var second=messages.list(login("student"),"inbox",1);
        assertThat(first.messages().content()).hasSize(20);
        assertThat(second.messages().content()).hasSize(2);
        assertThat(first.messages().content().getFirst().subject()).isEqualTo("Лист 20");
        assertThat(first.unreadCount()).isEqualTo(22);
    }
    @Test void subjectTeacherAccessUsesActiveScheduleAndFiltersBlockedAccounts() {
        long user=userId("subjectteacher","TEACHER");
        long teacher=id("insert into teacher(user_id,teacher_name,teacher_surname,teacher_birthdate,teacher_email,teacher_phone) values (?,'Тарас','Бойко','1980-01-01',?,'000') returning teacher_id",user,prefix+"subjectteacher@example.invalid");
        long schoolClass=id("select class_id from student where student_id=?",studentId);
        jdbc.update("update school_class set class_teacher_id=null where class_id=?",schoolClass);
        long subject=id("insert into subject(subject_title) values (?) returning subject_id",prefix+"subject");
        jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)",teacher,subject);
        jdbc.update("insert into schedule(class_id,subject_id,teacher_id,schedule_weekday,schedule_lesson_number,schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from) values (?,?,?,'MONDAY',1,'08:00','08:45','2026/2027',1,'2026-09-01')",schoolClass,subject,teacher);
        assertThat(messages.recipients(login("student"),"",null)).extracting(MessageDtos.Recipient::id).containsExactly(user);
        assertThat(messages.recipients(login("subjectteacher"),"",null)).extracting(MessageDtos.Recipient::id).contains(studentUser,parentUser);
        jdbc.update("update app_user set user_status='BLOCKED' where user_id=?",user);
        assertThat(messages.recipients(login("student"),"",null)).isEmpty();
    }
    @Test void homeroomTeacherRanksBeforeAlphabeticalContactsAndBeforeResultLimit() {
        long schoolClass=id("select class_id from student where student_id=?",studentId);
        long subject=id("insert into subject(subject_title) values (?) returning subject_id",prefix+"priority");
        String[] days={"MONDAY","TUESDAY","WEDNESDAY"};
        for(int index=0;index<21;index++) {
            long user=userId("teacher"+index,"TEACHER");
            long teacher=id("insert into teacher(user_id,teacher_name,teacher_surname,teacher_birthdate,teacher_email,teacher_phone) values (?,'Тестовий','Андрієнко','1980-01-01',?,'000') returning teacher_id",user,prefix+index+"@example.invalid");
            jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)",teacher,subject);
            jdbc.update("insert into schedule(class_id,subject_id,teacher_id,schedule_weekday,schedule_lesson_number,schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from) values (?,?,?,?::enum_weekday,?,'08:00','08:45','2026/2027',1,'2026-09-01')",schoolClass,subject,teacher,days[index/7],index%7+1);
        }
        for(String role:List.of("student","parent")) {
            var found=messages.recipients(login(role),"",null);
            assertThat(found).hasSize(20);
            assertThat(found.getFirst().id()).isEqualTo(teacherUser);
            assertThat(found.getFirst().homeroom()).isTrue();
            assertThat(found.subList(1,found.size())).allMatch(recipient->!recipient.homeroom());
            assertThat(messages.recipients(login(role),"Андрієнко",null)).noneMatch(MessageDtos.Recipient::homeroom);
        }
        assertThat(messages.recipients(login("teacher"),"",null)).noneMatch(MessageDtos.Recipient::homeroom);
    }
    @Test void administratorsHideOnlyTheirOwnProcessedEventsAndCannotHidePending() throws Exception {
        long first=userId("admin1","ADMIN"),second=userId("admin2","ADMIN");
        long schoolClass=id("select class_id from student where student_id=?",studentId);
        long pending=id("insert into registration_request(request_name,request_surname,request_email,request_phone,request_birthdate,requested_role,requested_class_id,request_terms_accepted_at) values ('Тест','Заявка',?,'000','2010-01-01','STUDENT',?,now()) returning request_id",prefix+"pending@example.invalid",schoolClass);
        long processed=id("insert into registration_request(request_name,request_surname,request_email,request_phone,request_birthdate,requested_role,requested_class_id,request_terms_accepted_at,request_status,request_reviewed_at,request_reviewed_by_user_id,request_rejection_reason) values ('Тест','Рішення',?,'000','2010-01-01','STUDENT',?,now(),'REJECTED',now(),?,'Причина') returning request_id",prefix+"rejected@example.invalid",schoolClass,first);
        assertThat(adminNotifications.feed(login("admin1")).items()).extracting(com.ediary.backend.registration.dto.response.RegistrationRequestResponse::id).contains(pending,processed);
        adminNotifications.dismiss(login("admin1"),processed);adminNotifications.dismiss(login("admin1"),processed);
        assertThat(adminNotifications.feed(login("admin1")).items()).extracting(com.ediary.backend.registration.dto.response.RegistrationRequestResponse::id).doesNotContain(processed).contains(pending);
        assertThat(adminNotifications.feed(login("admin2")).items()).extracting(com.ediary.backend.registration.dto.response.RegistrationRequestResponse::id).contains(processed);
        assertThatThrownBy(()->adminNotifications.dismiss(login("admin1"),pending)).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        adminNotifications.clear(login("admin2"));
        assertThat(adminNotifications.feed(login("admin2")).pendingCount()).isEqualTo(1);
        assertThat(adminNotifications.feed(login("admin2")).items()).hasSize(1);
        assertThat(id("select count(*) from registration_request where request_id in (?,?)",pending,processed)).isEqualTo(2);
        mvc.perform(delete("/api/admin/registration-requests/notifications/"+processed).with(user(login("student")).roles("STUDENT")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/registration-requests/notifications/"+processed).with(user(login("admin1")).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/registration-requests/notifications/"+processed).with(user(login("admin1")).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
    }
    @Test void clearNotificationsIsPersonalSoftHideAndDoesNotCancelMailOrDeleteMessages() throws Exception {
        jdbc.update("insert into user_notification_settings(user_id,grade_email_enabled) values (?,true)",studentUser);
        new TransactionTemplate(transactions).executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),"GRADE","Нова оцінка","Предмет: Інформатика\nОцінка: 7",prefix+"clear"));
        var message=messages.send(login("teacher"),new MessageDtos.Send(studentUser,"Лист","Текст",UUID.randomUUID()));
        long count=id("select count(*) from user_notification where recipient_id=?",studentUser);
        notifications.dismissAll(login("student"));
        assertThat(notifications.feed(login("student")).items()).isEmpty();
        assertThat(notifications.feed(login("student")).unreadCount()).isZero();
        assertThat(notifications.feed(login("parent")).items()).isNotEmpty();
        assertThat(id("select count(*) from user_notification where recipient_id=?",studentUser)).isEqualTo(count);
        assertThat(id("select count(*) from user_notification where recipient_id=? and email_kind='NOTICE' and email_due_at is not null",studentUser)).isEqualTo(1);
        assertThat(messages.list(login("student"),"inbox",0).messages().content()).extracting(MessageDtos.Item::id).contains(message.id());
        mvc.perform(delete("/api/notifications").with(user(login("student")).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notifications").with(user(login("student")).roles("STUDENT")).with(csrf())).andExpect(status().isOk());
    }
    @Test void gradeDeletionNotifiesStudentAndParentAndHonorsEmailOptIn() {
        when(email.isEnabled()).thenReturn(true);
        when(email.sendText(anyString(),anyString(),anyString())).thenReturn(true);
        jdbc.update("insert into user_notification_settings(user_id,grade_email_enabled) values (?,true),(?,false)",studentUser,parentUser);
        new TransactionTemplate(transactions).executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),
            "GRADE","Оцінку видалено: 11 із 12","Предмет: Інформатика\nТип роботи: Класна робота\nОцінку видалено: 11 із 12",null));
        for(String role:List.of("student","parent")) {
            var item=notifications.feed(login(role)).items().getFirst();
            assertThat(item.title()).isEqualTo("Оцінку видалено");
            assertThat(item.body()).contains("Інформатика · 11 із 12","Класна робота").doesNotContain("→");
            assertThat(item.target()).startsWith(role+"/subjects");
            sender.deliver(item.id());
        }
        verify(email).sendText(org.mockito.ArgumentMatchers.eq(login("student")+"@example.invalid"),
            org.mockito.ArgumentMatchers.eq("Оцінку видалено: 11 із 12"),org.mockito.ArgumentMatchers.argThat(letter->
                letter.startsWith("Оцінку видалено: 11 із 12\n")&&letter.indexOf("Оцінку видалено:")==letter.lastIndexOf("Оцінку видалено:")));
        verify(email,times(1)).sendText(anyString(),anyString(),anyString());
    }

    @Test void assessmentPoliciesPreserveMarksAndApplyToJournalAndLearnerResults() throws Exception {
        var numeric=new com.ediary.backend.journal.service.AssessmentPolicyService.Policy(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.NUMERIC,true,true);
        var passFail=new com.ediary.backend.journal.service.AssessmentPolicyService.Policy(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.PASS_FAIL,false,true);
        long classId=id("select class_id from student where student_id=?",studentId);
        long teacherId=id("select teacher_id from teacher where user_id=?",teacherUser);
        long subjectId=id("insert into subject(subject_title) values (?) returning subject_id","Курс "+prefix);
        jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)",teacherId,subjectId);
        long scheduleId=id("insert into schedule(class_id,teacher_id,subject_id,schedule_weekday,schedule_lesson_number,schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from) values (?,?,?,'MONDAY',1,'08:00','08:45','2026/2027',1,'2026-09-01') returning schedule_id",classId,teacherId,subjectId);
        long lessonId=id("insert into lesson(schedule_id,lesson_date,lesson_topic,lesson_work_type) values (?,'2026-10-19','Тема уроку','THEMATIC') returning lesson_id",scheduleId);
        journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(11,null,null,null));
        assertThatThrownBy(()->assessment.save(subjectId,passFail)).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        assertThat(id("select mark_score from mark where lesson_id=?",lessonId)).isEqualTo(11);
        assessment.save(subjectId,numeric);
        assertThat(assessment.find(subjectId)).isEqualTo(numeric);
        assertThat(assessment.hasResults(subjectId)).isTrue();
        journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,null,null,null,com.ediary.backend.journal.entity.GradeResult.PASSED));
        assertThat(jdbc.queryForObject("select mark_result from mark where lesson_id=?",String.class,lessonId)).isEqualTo("PASSED");
        assertThatThrownBy(()->assessment.save(subjectId,new com.ediary.backend.journal.service.AssessmentPolicyService.Policy(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.NUMERIC,false,true))).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        for(String role:List.of("student","parent")) {
            var response=role.equals("student")?progress.forStudent(login(role),"2026/2027"):progress.forParent(login(role),studentId,"2026/2027");
            assertThat(response.subjects()).anySatisfy(subject->{assertThat(subject.id()).isEqualTo(subjectId);assertThat(subject.grades().getFirst().score()).isNull();assertThat(subject.grades().getFirst().result()).isEqualTo(com.ediary.backend.journal.entity.GradeResult.PASSED);});
        }
        journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,null,null,null,com.ediary.backend.journal.entity.GradeResult.EXEMPTED));
        assertThat(jdbc.queryForObject("select mark_result from mark where lesson_id=?",String.class,lessonId)).isEqualTo("EXEMPTED");
        assertThatThrownBy(()->assessment.save(subjectId,new com.ediary.backend.journal.service.AssessmentPolicyService.Policy(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.NUMERIC,true,false))).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,null,null,null));
        assessment.save(subjectId,passFail);
        assertThatThrownBy(()->journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(8,null,null,null))).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,null,null,null,com.ediary.backend.journal.entity.GradeResult.NOT_PASSED));
        assertThat(jdbc.queryForObject("select mark_result from mark where lesson_id=?",String.class,lessonId)).isEqualTo("NOT_PASSED");
        var feed=notifications.feed(login("student"));
        assertThat(feed.items()).anySatisfy(item->{assertThat(item.title()).isEqualTo("Результат");assertThat(item.body()).contains("не зар.").doesNotContain("із 12");});
        String policyUrl="/api/admin/subjects/"+subjectId;
        mvc.perform(patch(policyUrl).with(user(login("teacher")).roles("TEACHER")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch(policyUrl).with(user(login("admin")).roles("ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        jdbc.update("update lesson set lesson_work_type='CLASSWORK' where lesson_id=?",lessonId);
        assertThatThrownBy(()->journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,null,null,null,com.ediary.backend.journal.entity.GradeResult.PASSED))).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        assertThatThrownBy(()->journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,com.ediary.backend.journal.entity.AttendanceStatus.EXEMPTED,null,null))).isInstanceOf(com.ediary.backend.common.error.ApplicationException.class);
        journal.saveCell(login("teacher"),lessonId,studentId,new com.ediary.backend.journal.dto.SaveJournalCellRequest(null,com.ediary.backend.journal.entity.AttendanceStatus.PRESENT,null,null));
        assertThat(id("select count(*) from mark where lesson_id=?",lessonId)).isZero();
        assertThat(jdbc.queryForObject("select attendance_status::text from attendance where lesson_id=?",String.class,lessonId)).isEqualTo("PRESENT");
    }

    @Test void subjectCreateAndEditOwnGlobalAssessmentSettings() throws Exception {
        String title="Глобальний "+prefix;
        mvc.perform(post("/api/admin/subjects").with(user(login("admin")).roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"title\":\""+title+"\",\"teacherIds\":[],\"assessmentPolicy\":{\"mode\":\"PASS_FAIL\",\"allowPassed\":false,\"allowExempted\":true}}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.assessmentPolicy.mode").value("PASS_FAIL"))
            .andExpect(jsonPath("$.assessmentPolicy.allowExempted").value(true)).andExpect(jsonPath("$.hasResults").value(false));
        long subjectId=id("select subject_id from subject where subject_title=?",title);
        mvc.perform(patch("/api/admin/subjects/"+subjectId).with(user(login("admin")).roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"title\":\""+title+"\",\"teacherIds\":[],\"assessmentPolicy\":{\"mode\":\"NUMERIC\",\"allowPassed\":true,\"allowExempted\":false}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assessmentPolicy.mode").value("NUMERIC"))
            .andExpect(jsonPath("$.assessmentPolicy.allowPassed").value(true));
        assertThat(assessment.find(subjectId).allowExempted()).isFalse();
        String migration;
        try(var stream=new org.springframework.core.io.ClassPathResource("db/migration/V32__subject_wide_assessment_policy.sql").getInputStream()) {
            migration=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        new TransactionTemplate(transactions).executeWithoutResult(transaction->{
            // Recreate the pre-V32 source only inside this rollback-only migration test.
            try(var stream=new org.springframework.core.io.ClassPathResource("db/migration/V31__assessment_results_and_policy.sql").getInputStream()) {
                String source=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                jdbc.execute(source.substring(source.indexOf("CREATE TABLE assessment_policy")));
            } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            long classId=id("select class_id from student where student_id=?",studentId);
            long teacherId=id("select teacher_id from teacher where user_id=?",teacherUser);
            jdbc.update("insert into assessment_policy(class_id,subject_id,mode,allow_passed,allow_exempted) values (?,?,'PASS_FAIL',false,true)",classId,subjectId);
            long numericSubject=id("insert into subject(subject_title) values (?) returning subject_id","Історичний "+prefix);
            jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)",teacherId,numericSubject);
            jdbc.update("insert into assessment_policy(class_id,subject_id,mode) values (?,?,'PASS_FAIL')",classId,numericSubject);
            long schedule=id("insert into schedule(class_id,teacher_id,subject_id,schedule_weekday,schedule_lesson_number,schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from) values (?,?,?,'MONDAY',1,'08:00','08:45','2026/2027',1,'2026-09-01') returning schedule_id",classId,teacherId,numericSubject);
            long lesson=id("insert into lesson(schedule_id,lesson_date,lesson_topic) values (?,'2026-10-19','Історична тема') returning lesson_id",schedule);
            jdbc.update("insert into mark(student_id,lesson_id,mark_type,mark_score) values (?,?,'CURRENT',11)",studentId,lesson);
            jdbc.execute("drop table subject_assessment_policy");
            jdbc.execute(migration);
            assertThat(assessment.find(subjectId).mode()).isEqualTo(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.PASS_FAIL);
            assertThat(assessment.find(subjectId).allowExempted()).isTrue();
            assertThat(assessment.find(numericSubject).mode()).isEqualTo(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.NUMERIC);
            assertThat(assessment.find(numericSubject).allowPassed()).isTrue();
            assertThat(id("select mark_score from mark where lesson_id=?",lesson)).isEqualTo(11);
            long otherLesson=id("insert into lesson(schedule_id,lesson_date,lesson_topic) values (?,'2026-10-26','Інша тема') returning lesson_id",schedule);
            jdbc.update("insert into mark(student_id,lesson_id,mark_type,mark_result) values (?,?,'TOPIC','NOT_PASSED')",studentId,otherLesson);
            jdbc.execute("drop table subject_assessment_policy");
            assertThatThrownBy(()->jdbc.execute(migration)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            transaction.setRollbackOnly();
        });
        assertThat(assessment.find(subjectId).mode()).isEqualTo(com.ediary.backend.journal.service.AssessmentPolicyService.Mode.NUMERIC);
    }

    @Test void userImportPreviewConfirmationSecurityAndDuplicateProtection() throws Exception {
        when(email.sendText(anyString(),anyString(),anyString())).thenReturn(true);
        String subject="Імпорт "+prefix;
        jdbc.update("insert into subject(subject_title) values (?)",subject);
        var upload=importCsv("Вчитель",login("import"),subject,"","","");
        long before=id("select count(*) from app_user");
        var preview=userImports.preview(upload,"csv");
        assertThat(preview.invalid()).isZero();
        assertThat(id("select count(*) from app_user")).isEqualTo(before);
        verify(email,never()).sendText(anyString(),anyString(),anyString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/admin/users/import/preview").file(upload).param("format","csv").with(user("teacher").roles("TEACHER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/admin/users/import/confirm").file(upload).param("format","csv").param("fingerprint",preview.fingerprint()).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/import/template").param("format","xlsx").with(user("teacher").roles("TEACHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/import/template").param("format","xlsx").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=users-template.xlsx"));
        assertThatThrownBy(()->userImports.confirm(upload,"csv","wrong")).hasMessageContaining("змінився");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/admin/users/import/confirm").file(upload).param("format","csv").param("fingerprint",preview.fingerprint()).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
        assertThat(id("select count(*) from app_user")).isEqualTo(before+1);
        assertThat(jdbc.queryForObject("select user_must_change_password from app_user where user_login=?",Boolean.class,login("import"))).isTrue();
        verify(email,times(1)).sendText(anyString(),anyString(),anyString());
        assertThatThrownBy(()->userImports.confirm(upload,"csv",preview.fingerprint())).hasMessageContaining("повторну перевірку");
        assertThat(userImports.preview(upload,"csv").rows().getFirst().errors()).containsKey("Логін");
        String changedLogin=new String(upload.getBytes(),java.nio.charset.StandardCharsets.UTF_8)
            .replaceFirst(java.util.regex.Pattern.quote(login("import"))+";",login("other")+";");
        var duplicateEmail=new org.springframework.mock.web.MockMultipartFile("file","users.csv","text/csv",changedLogin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(userImports.preview(duplicateEmail,"csv").rows().getFirst().errors()).containsKey("Електронна пошта");
        assertThat(id("select count(*) from app_user")).isEqualTo(before+1);
    }

    @Test void userImportHandlesParentLinksStudentsAndFileErrors() throws Exception {
        long schoolClass=id("select class_id from student where student_id=?",studentId);
        String className=jdbc.queryForObject("select class_number||'-'||class_letter from school_class where class_id=?",String.class,schoolClass);
        var student=importCsv("Учень",login("newstudent"),"",className,"2026/2027","");
        var parent=importCsv("Батьки",login("newparent"),"","","",login("newstudent").toUpperCase(Locale.ROOT));
        var existing=importCsv("Батьки",login("existingchild"),"","","",login("student"));
        var batch=combineCsv(parent,student,existing);
        var preview=userImports.preview(batch,"csv");
        assertThat(preview.invalid()).isZero();
        assertThat(preview.rows().getFirst().values().get(11)).isEqualTo(login("newstudent").toUpperCase(Locale.ROOT));
        assertThat(preview.rows().getFirst().linkedStudent().fullName()).isEqualTo("Петренко Іван Олександрович");
        assertThat(preview.rows().getFirst().linkedStudent().className()).isEqualTo(className);
        assertThat(preview.rows().getLast().linkedStudent().fullName()).isEqualTo("Петренко Іван");
        verifyNoInteractions(email);
        assertThat(userImports.confirm(batch,"csv",preview.fingerprint()).created()).isEqualTo(3);
        var imported=students.findByUser_LoginIgnoreCase(login("newstudent")).orElseThrow();
        assertThat(imported.getParentLinkCodeHash()).isNotBlank();
        assertThat(linkedStudentId(login("newparent"))).isEqualTo(imported.getId());
        assertThat(linkedStudentId(login("existingchild"))).isEqualTo(studentId);
        verify(email,times(3)).sendText(anyString(),anyString(),anyString());
        assertThat(userImports.preview(importCsv("ADMIN",login("badadmin"),"","","",""),"csv").invalid()).isEqualTo(1);
        for (String login : List.of("", "not-found", login("teacher"))) {
            var invalidParent=importCsv("Батьки",login("badparent"),"","","",login);
            assertThat(userImports.preview(invalidParent,"csv").rows().getFirst().errors()).containsKey("Логін учня");
        }
        var invalidStudent=importCsv("Учень",login("badclass"),"",className,"2025/2026","");
        var dependsOnInvalid=importCsv("Батьки",login("dependent"),"","","",login("badclass"));
        var invalidBatch=userImports.preview(combineCsv(dependsOnInvalid,invalidStudent),"csv");
        assertThat(invalidBatch.invalid()).isEqualTo(2);
        assertThat(invalidBatch.rows().getFirst().errors()).containsKey("Логін учня");
        var duplicate=combineCsv(student,student);
        assertThat(userImports.preview(duplicate,"csv").invalid()).isEqualTo(2);
        assertThatThrownBy(()->userImports.confirm(duplicate,"csv",userImports.preview(duplicate,"csv").fingerprint())).hasMessageContaining("Жодного");
    }

    @Test void userImportRollsBackEarlierRowsAndNeverSendsCredentialsOnDatabaseFailure() throws Exception {
        String className=jdbc.queryForObject("select class_number||'-'||class_letter from school_class join student using(class_id) where student_id=?",String.class,studentId);
        var student=importCsv("Учень",login("batchone"),"",className,"2026/2027","");
        var parent=importCsv("Батьки",login("batchtwo"),"","","",login("batchone"));
        var file=combineCsv(parent,student);
        var preview=userImports.preview(file,"csv");assertThat(preview.invalid()).isZero();
        jdbc.execute("create function fail_import() returns trigger language plpgsql as $$ begin raise exception 'Test import failure' using errcode='23514'; end $$");
        jdbc.execute("create trigger fail_import before insert on student_parents for each row execute function fail_import()");
        try { assertThatThrownBy(()->userImports.confirm(file,"csv",preview.fingerprint())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("drop trigger fail_import on student_parents");jdbc.execute("drop function fail_import()"); }
        assertThat(id("select count(*) from app_user where user_login in (?,?)",login("batchone"),login("batchtwo"))).isZero();
        verify(email,never()).sendText(anyString(),anyString(),anyString());
    }

    @Test void adminSearchesLinkableStudentsAndCreatesParentUsingSelectedId() throws Exception {
        mvc.perform(get("/api/admin/users/students").param("query",login("student")).with(user("teacher").roles("TEACHER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/students").param("query","Петренко Іван").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].fullName").value("Петренко Іван"));
        mvc.perform(get("/api/admin/users/students").param("query",login("student").toUpperCase(Locale.ROOT)).with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(studentId))
            .andExpect(jsonPath("$[0].className").isString());
        mvc.perform(get("/api/admin/users/students").param("query","%%").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        String payload="""
            {"role":"PARENT","login":"%s","firstName":"Іван","lastName":"Петренко",
             "email":"%s@example.invalid","birthDate":"1980-04-15","phone":"+380671112233","studentId":%d}
            """;
        long missingStudentId=Long.MAX_VALUE;
        mvc.perform(post("/api/admin/users").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(payload.formatted(login("manual"),login("manual"),missingStudentId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LINKED_STUDENT"));
        mvc.perform(post("/api/admin/users").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(payload.formatted(login("manual"),login("manual"),studentId)))
            .andExpect(status().isCreated());
        assertThat(linkedStudentId(login("manual"))).isEqualTo(studentId);
    }

    @Test void importsBothWeeksWithExplicitDecisionsAndPreservesUnlistedLessons() throws Exception {
        var target=scheduleImportTarget();
        long recorded=scheduleSlot(target,target.classId(),1,"MONDAY",1,"101");
        long kept=scheduleSlot(target,target.classId(),1,"MONDAY",2,"102");
        long same=scheduleSlot(target,target.classId(),2,"FRIDAY",1,"103");
        long unlisted=scheduleSlot(target,target.classId(),1,"MONDAY",4,"104");
        jdbc.update("insert into lesson(schedule_id,lesson_date,lesson_status) values (?,'2026-09-14','COMPLETED')",recorded);
        var file=scheduleCsv("1;Понеділок;1;"+target.subject()+";"+login("teacher")+";201",
            "1;Понеділок;2;"+target.subject()+";"+login("teacher")+";202",
            "2;П’ятниця;1;"+target.subject()+";"+login("teacher")+";103",
            "1;Вівторок;3;"+target.subject()+";"+login("teacher")+";203",
            "2;П'ятниця;3;"+target.subject().toUpperCase(Locale.ROOT)+";"+login("teacher").toUpperCase(Locale.ROOT)+";204");
        var preview=scheduleImports.preview(target.classId(),"2026/2027",file,"csv");
        assertThat(preview.rows()).extracting(row->row.status())
            .containsExactly(Status.CONFLICT,Status.CONFLICT,Status.UNCHANGED,Status.ADD,Status.ADD);
        assertThat(preview.rows().getFirst().current().room()).isEqualTo("101");
        assertThat(preview.rows().getFirst().proposed().room()).isEqualTo("201");
        assertThat(preview.rows().getLast().proposed().startTime()).isEqualTo("09:50");
        assertThat(scheduleSlotCount(target.classId())).isEqualTo(4);
        assertThatThrownBy(()->scheduleImports.confirm(target.classId(),"2026/2027",file,"csv",preview.fingerprint(),List.of(),List.of(2)))
            .hasMessageContaining("кожним зайнятим");
        assertThat(scheduleSlotCount(target.classId())).isEqualTo(4);

        String endpoint="/api/admin/schedules/import";
        String classId=Long.toString(target.classId());
        mvc.perform(multipart(endpoint+"/preview").file(file).param("classId",classId).param("academicYear","2026/2027")
            .param("format","csv").with(user("teacher").roles("TEACHER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint+"/confirm").file(file).param("classId",classId).param("academicYear","2026/2027")
            .param("format","csv").param("fingerprint",preview.fingerprint()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint+"/confirm").file(file).param("classId",classId).param("academicYear","2026/2027")
            .param("format","csv").param("fingerprint",preview.fingerprint()).param("keepRows","3").param("replaceRows","2")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.added").value(2)).andExpect(jsonPath("$.replaced").value(1))
            .andExpect(jsonPath("$.kept").value(1)).andExpect(jsonPath("$.unchanged").value(1));
        assertThat(scheduleSlotCount(target.classId())).isEqualTo(6);
        assertThat(jdbc.queryForObject("select schedule_active from schedule where schedule_id=?",Boolean.class,recorded)).isFalse();
        assertThat(id("select count(*) from lesson where schedule_id=?",recorded)).isEqualTo(1);
        assertThat(jdbc.queryForList("select schedule_room from schedule where schedule_id in (?,?,?) order by schedule_room",
            String.class,kept,same,unlisted)).containsExactly("102","103","104");
        assertThat(jdbc.queryForList("select schedule_room from schedule where class_id=? and schedule_active and schedule_room in ('201','203','204') order by schedule_room",
            String.class,target.classId())).containsExactly("201","203","204");
        assertThatThrownBy(()->scheduleImports.confirm(target.classId(),"2026/2027",file,"csv",preview.fingerprint(),List.of(3),List.of(2)))
            .hasMessageContaining("змінився");
    }

    @Test void scheduleImportValidatesRowsAndRejectsArchivedOrMissingClasses() throws Exception {
        var target=scheduleImportTarget();
        long other=id("insert into school_class(class_number,class_letter,class_academic_year) values (1,'Ї','2026/2027') returning class_id");
        scheduleSlot(target,other,1,"MONDAY",1,"101");
        String valid=target.subject()+";"+login("teacher")+";101";
        var file=scheduleCsv("1;Понеділок;1;"+valid,
            "1;Вівторок;2;"+valid,"1;Вівторок;2;"+valid,
            "3;Неділя;9;Невідомий;неіснуючий;!", "1;Середа;3;"+target.subject()+";"+login("teacher")+";!");
        var preview=scheduleImports.preview(target.classId(),"2026/2027",file,"csv");
        assertThat(preview.rows()).allMatch(row->row.status()==Status.INVALID);
        assertThat(preview.rows().get(0).errors()).anyMatch(error->error.contains("іншому класі"));
        assertThat(preview.rows().get(1).errors()).anyMatch(error->error.contains("повторюється"));
        assertThat(preview.rows().get(3).errors()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(preview.rows().getLast().errors()).anyMatch(error->error.contains("Кабінет"));
        assertThat(preview.rows().getFirst().cellErrors()).containsOnlyKeys(Column.TEACHER);
        assertThat(preview.rows().get(1).cellErrors()).containsOnlyKeys(Column.LESSON);
        assertThat(preview.rows().get(3).cellErrors()).containsOnlyKeys(Column.values());
        assertThat(preview.rows().getLast().cellErrors()).containsOnlyKeys(Column.ROOM);
        mvc.perform(multipart("/api/admin/schedules/import/preview").file(file).param("format","csv")
            .param("classId",Long.toString(target.classId())).param("academicYear","2026/2027")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].cellErrors.TEACHER[0]").isString())
            .andExpect(jsonPath("$.rows[0].errors[0]").isString());
        assertThatThrownBy(()->scheduleImports.confirm(target.classId(),"2026/2027",file,"csv",preview.fingerprint(),List.of(),List.of()))
            .hasMessageContaining("Виправте помилки");
        assertThatThrownBy(()->scheduleImports.preview(target.classId(),"2025/2026",file,"csv"))
            .hasMessageContaining("поточного навчального року");
        assertThatThrownBy(()->scheduleImports.preview(null,"2026/2027",file,"csv"))
            .hasMessageContaining("Оберіть клас");
        assertThatThrownBy(()->scheduleImports.preview(other,"2026/2027",file,"csv"))
            .hasMessageContaining("класного керівника");
        assertThat(scheduleSlotCount(target.classId())).isZero();
    }

    @Test void xlsxScheduleImportRollsBackFirstWeekWhenSecondWeekCannotBeSaved() throws Exception {
        var target=scheduleImportTarget();
        byte[] bytes;
        try (var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(scheduleImports.template("xlsx")));
             var output=new ByteArrayOutputStream()) {
            var sheet=book.getSheetAt(0);
            for (int i=1;i<=2;i++) {
                sheet.getRow(i).getCell(3).setCellValue(target.subject());
                sheet.getRow(i).getCell(4).setCellValue(login("teacher"));
            }
            book.write(output);
            bytes=output.toByteArray();
        }
        var file=new MockMultipartFile("file","schedule.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);
        var preview=scheduleImports.preview(target.classId(),"2026/2027",file,"xlsx");
        assertThat(preview.rows()).extracting(row->row.status()).containsExactly(Status.ADD,Status.ADD);
        jdbc.execute("""
            create function fail_schedule_import() returns trigger language plpgsql as $$
            begin
                if new.schedule_week_number=2 then raise exception 'Test second week failure' using errcode='23514'; end if;
                return new;
            end $$;
            """);
        jdbc.execute("create trigger fail_schedule_import before insert on schedule for each row execute function fail_schedule_import()");
        try {
            assertThatThrownBy(()->scheduleImports.confirm(target.classId(),"2026/2027",file,"xlsx",preview.fingerprint(),List.of(),List.of()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("drop trigger fail_schedule_import on schedule");
            jdbc.execute("drop function fail_schedule_import()");
        }
        assertThat(scheduleSlotCount(target.classId())).isZero();
        assertThat(notifications.feed(login("student")).items()).isEmpty();
        assertThat(scheduleImports.confirm(target.classId(),"2026/2027",file,"xlsx",preview.fingerprint(),List.of(),List.of()).added()).isEqualTo(2);
        assertThat(scheduleSlotCount(target.classId())).isEqualTo(2);
    }

    private record ScheduleImportTarget(long classId,long teacherId,long subjectId,String subject) {}

    private ScheduleImportTarget scheduleImportTarget() {
        long classId=id("select class_id from student where student_id=?",studentId);
        long teacherId=id("select teacher_id from teacher where user_id=?",teacherUser);
        String title="Розклад "+prefix;
        long subjectId=id("insert into subject(subject_title) values (?) returning subject_id",title);
        jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)",teacherId,subjectId);
        return new ScheduleImportTarget(classId,teacherId,subjectId,title);
    }

    private long scheduleSlot(ScheduleImportTarget target,long classId,int week,String day,int number,String room) {
        var times=List.of("08:00","08:55","09:50","10:45");
        var ends=List.of("08:45","09:40","10:35","11:30");
        return id("""
            insert into schedule(class_id,subject_id,teacher_id,schedule_weekday,schedule_lesson_number,
                schedule_room,schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from)
            values (?,?,?,?::enum_weekday,?,?,?::time,?::time,'2026/2027',?,'2026-09-01') returning schedule_id
            """,classId,target.subjectId(),target.teacherId(),day,number,room,times.get(number-1),ends.get(number-1),week);
    }

    private long scheduleSlotCount(long classId) {
        return id("select count(*) from schedule where class_id=? and schedule_active",classId);
    }

    private MockMultipartFile scheduleCsv(String... rows) {
        String text="Тиждень;День;Номер уроку;Предмет;Логін учителя;Кабінет\n"+String.join("\n",rows);
        return new MockMultipartFile("file","schedule.csv","text/csv",text.getBytes(StandardCharsets.UTF_8));
    }

    @Test void historicalRosterAndGradesUsePreviousClassWithoutMovingStudent() throws Exception {
        long currentClass=id("select class_id from student where student_id=?",studentId);
        long teacher=id("select teacher_id from teacher where user_id=?",teacherUser);
        jdbc.update("insert into academic_year(academic_year_value) values ('2025/2026')");
        long pastClass=id("""
            insert into school_class(class_number,class_letter,class_academic_year,class_teacher_id)
            values (8,'А','2025/2026',?) returning class_id
            """,teacher);
        jdbc.update("""
            insert into student_class_membership(student_id,class_id,membership_active,joined_at,left_at)
            values (?,?,false,'2025-09-01','2026-06-01')
            """,studentId,pastClass);
        long subject=id("insert into subject(subject_title) values (?) returning subject_id","Архів "+prefix);
        jdbc.update("insert into teacher_subject(teacher_id,subject_id) values (?,?)",teacher,subject);
        long slot=id("""
            insert into schedule(class_id,teacher_id,subject_id,schedule_weekday,schedule_lesson_number,
                schedule_start_time,schedule_end_time,schedule_academic_year,schedule_week_number,schedule_effective_from)
            values (?,?,?,'MONDAY',1,'08:00','08:45','2025/2026',1,'2025-09-01') returning schedule_id
            """,pastClass,teacher,subject);
        long lesson=id("""
            insert into lesson(schedule_id,lesson_date,lesson_work_type,lesson_topic,lesson_status)
            values (?,'2026-05-18','YEAR','Підсумок за рік','COMPLETED') returning lesson_id
            """,slot);
        jdbc.update("insert into mark(student_id,lesson_id,mark_date,mark_type,mark_score) values (?,?,'2026-05-18','YEAR',11)",
            studentId,lesson);
        jdbc.update("insert into attendance(student_id,lesson_id,attendance_status) values (?,?,'ABSENT')",studentId,lesson);

        mvc.perform(get("/api/admin/classes/{id}/students",pastClass).with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(studentId));
        mvc.perform(get("/api/admin/schedules").param("classId",String.valueOf(pastClass)).param("academicYear","2025/2026")
            .param("week","1").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/schedules").param("classId",String.valueOf(pastClass)).param("academicYear","2025/2026")
            .param("week","1").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"lessons\":[]}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_YEAR_READ_ONLY"));
        var archive=journal.find(login("teacher"),pastClass,subject,"2026-05");
        assertThat(archive.students()).extracting(student->student.id()).containsExactly(studentId);
        assertThat(archive.lessons()).anySatisfy(item->{
            assertThat(item.id()).isEqualTo(lesson);
            assertThat(item.cells()).singleElement().satisfies(cell->assertThat(cell.score()).isEqualTo(11));
        });
        var teacherAuth=user(login("teacher")).roles("TEACHER");
        mvc.perform(get("/api/teacher/journal").param("classId",String.valueOf(pastClass)).param("subjectId",String.valueOf(subject))
            .param("month","2026-05").with(teacherAuth)).andExpect(status().isOk());
        mvc.perform(put("/api/teacher/journal/lessons/schedule/{id}",slot).param("date","2026-05-18")
            .with(teacherAuth).with(csrf()).contentType("application/json")
            .content("{\"topic\":\"Інша тема\",\"workType\":\"YEAR\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOURNAL_YEAR_READ_ONLY"));
        mvc.perform(put("/api/teacher/journal/lessons/{id}/students/{studentId}",lesson,studentId)
            .with(teacherAuth).with(csrf()).contentType("application/json").content("{\"score\":12}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOURNAL_YEAR_READ_ONLY"));
        mvc.perform(put("/api/teacher/journal/lessons/schedule/{id}/attendance",slot).param("date","2026-05-18")
            .with(teacherAuth).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOURNAL_YEAR_READ_ONLY"));
        mvc.perform(delete("/api/teacher/journal/lessons/{id}/attendance",lesson).with(teacherAuth).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOURNAL_YEAR_READ_ONLY"));
        mvc.perform(put("/api/teacher/lessons/{id}",lesson).with(teacherAuth).with(csrf())
            .contentType("application/json").content("{\"topic\":\"Інша тема\",\"status\":\"COMPLETED\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOURNAL_YEAR_READ_ONLY"));
        mvc.perform(put("/api/teacher/lessons/schedule/{id}",slot).param("date","2026-05-25").with(teacherAuth).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOURNAL_YEAR_READ_ONLY"));
        assertThat(id("select count(*) from lesson where schedule_id=?",slot)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select lesson_topic from lesson where lesson_id=?",String.class,lesson))
            .isEqualTo("Підсумок за рік");
        assertThat(jdbc.queryForObject("select attendance_status::text from attendance where lesson_id=?",String.class,lesson))
            .isEqualTo("ABSENT");
        assertThat(progress.forStudent(login("student"),"2025/2026").subjects().getFirst().grades())
            .singleElement().satisfies(grade->assertThat(grade.score()).isEqualTo(11));
        assertThat(progress.forParent(login("parent"),studentId,"2025/2026").subjects().getFirst().grades())
            .singleElement().satisfies(grade->assertThat(grade.score()).isEqualTo(11));
        assertThat(id("select class_id from student where student_id=?",studentId)).isEqualTo(currentClass);
    }

    @Test
    void temporaryPasswordRestrictsAuthenticatedSessionUntilChanged() throws Exception {
        String temporary = "Aa1!" + UUID.randomUUID();
        String permanent = "NewStudent!2026";
        jdbc.update("update app_user set user_password_hash=?, user_must_change_password=true where user_id=?",
            passwordEncoder.encode(temporary), studentUser);

        String credentials = """
            {"login":"%s","password":"%s"}
            """.formatted(login("student"), temporary);
        var loginResult = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(credentials))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn();
        var session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/security/csrf").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").session(session))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/notifications/read-all").session(session).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        String change = """
            {"currentPassword":"%s","newPassword":"%s"}
            """.formatted(temporary, permanent);
        mvc.perform(post("/api/users/me/password").session(session).contentType("application/json").content(change))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/users/me/password").session(session).with(csrf()).contentType("application/json")
            .content(change.replace(temporary, "Incorrect!2026")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));

        mvc.perform(post("/api/users/me/password").session(session).with(csrf()).contentType("application/json").content(change))
            .andExpect(status().isOk());
        mvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(get("/api/notifications").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(credentials))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content(credentials.replace(temporary, permanent)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test void adminCanChangeOwnPasswordButManagementCannotResetOrChangeAdminStatus() throws Exception {
        long adminId=administrator("admin");
        long otherId=administrator("otheradmin");
        var admin=user(login("admin")).roles("ADMIN");
        String change="{\"currentPassword\":\"OldAdmin!2026\",\"newPassword\":\"NewAdmin!2026\"}";
        jdbc.update("update app_user set user_must_change_password=true where user_id=?",adminId);
        mvc.perform(get("/api/admin/users/me").with(admin))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/admin/users/me/password").with(user(login("teacher")).roles("TEACHER")).with(csrf())
            .contentType("application/json").content(change)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/me/password").with(admin).contentType("application/json").content(change))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/me/password").with(admin).with(csrf()).contentType("application/json")
            .content(change.replace("OldAdmin!2026","Incorrect!2026")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(post("/api/admin/users/me/password").with(admin).with(csrf()).contentType("application/json")
            .content(change.replace("NewAdmin!2026","weak")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.newPassword").isString());
        mvc.perform(post("/api/admin/users/me/password").with(admin).with(csrf()).contentType("application/json")
            .content(change.replace("NewAdmin!2026","OldAdmin!2026")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NEW_PASSWORD"));
        mvc.perform(post("/api/admin/users/me/password").with(admin).with(csrf()).contentType("application/json").content(change))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/users/me").with(admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        assertThat(passwordEncoder.matches("NewAdmin!2026",
            jdbc.queryForObject("select user_password_hash from app_user where user_id=?",String.class,adminId))).isTrue();
        assertThat(passwordEncoder.matches("OldAdmin!2026",
            jdbc.queryForObject("select user_password_hash from app_user where user_id=?",String.class,otherId))).isTrue();
        for (long target : new long[]{adminId,otherId}) {
            mvc.perform(post("/api/admin/users/{id}/reset-password",target).with(admin).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_PASSWORD_RESET_FORBIDDEN"));
            mvc.perform(patch("/api/admin/users/{id}/status",target).with(admin).with(csrf())
                .contentType("application/json").content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_STATUS_IS_PROTECTED"));
        }
        jdbc.update("update app_user set user_status='BLOCKED' where user_id=?",otherId);
        mvc.perform(patch("/api/admin/users/{id}/status",otherId).with(admin).with(csrf())
            .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_STATUS_IS_PROTECTED"));
        mvc.perform(patch("/api/admin/users/{id}",otherId).with(admin).with(csrf()).contentType("application/json")
            .content("""
                {"login":"%s","email":"different@example.invalid","firstName":"Олена","lastName":"Ткаченко","role":"ADMIN"}
                """.formatted(login("otheradmin"))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_CREDENTIALS_ARE_PROTECTED"));
        verifyNoInteractions(email);
    }

    @Test void adminCanRecoverPasswordByEmailAndStillNeedsAccessCodeToSignIn() throws Exception {
        administrator("recovery");
        String address=login("recovery")+"@example.invalid";
        when(email.sendText(eq(address),anyString(),anyString())).thenReturn(true);
        mvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType("application/json")
            .content("{\"email\":\""+address+"\"}")).andExpect(status().isOk());
        var body=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email,timeout(5000)).sendText(eq(address),anyString(),body.capture());
        var link=java.util.regex.Pattern.compile("/admin/reset-password\\?token=([A-Za-z0-9_-]{43})").matcher(body.getValue());
        assertThat(link.find()).isTrue();
        String reset="{\"token\":\""+link.group(1)+"\",\"newPassword\":\"Recovered!2026\"}";
        mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType("application/json").content(reset))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType("application/json").content(reset))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        String loginPayload="{\"login\":\""+login("recovery")+"\",\"password\":\"Recovered!2026\",\"accessCode\":\"ABC-123\"}";
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType("application/json")
            .content(loginPayload.replace("ABC-123","ABC-999"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType("application/json")
            .content(loginPayload.replace("Recovered!2026","OldAdmin!2026"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType("application/json").content(loginPayload))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    private long administrator(String suffix) {
        long accountId=userId(suffix,"ADMIN");
        jdbc.update("update app_user set user_password_hash=? where user_id=?",passwordEncoder.encode("OldAdmin!2026"),accountId);
        jdbc.update("insert into administrator(user_id,admin_name,admin_surname,admin_code_hash) values (?,'Олена','Ткаченко',?)",
            accountId,passwordEncoder.encode("ABC123"));
        return accountId;
    }

    @Test void xlsxImportRevalidatesAndCreatesTeacherProfile() throws Exception {
        String subject="XLSX "+prefix;jdbc.update("insert into subject(subject_title) values (?)",subject);
        var row=importFiles.parse(importCsv("Вчитель",login("xlsx"),subject,"","","").getBytes(),"csv").getFirst();
        byte[] bytes;
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()) {
            var sheet=book.createSheet("Користувачі");
            for(int index=0;index<2;index++) {
                var cells=sheet.createRow(index);var values=index==0?com.ediary.backend.user.admin.service.UserImportFileService.COLUMNS:row.values();
                for(int col=0;col<values.size();col++)cells.createCell(col).setCellValue(values.get(col));
            }
            book.write(out);bytes=out.toByteArray();
        }
        var file=new org.springframework.mock.web.MockMultipartFile("file","users.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);
        var preview=userImports.preview(file,"xlsx");assertThat(preview.invalid()).isZero();
        userImports.confirm(file,"xlsx",preview.fingerprint());
        assertThat(id("select count(*) from teacher_subject ts join teacher t using(teacher_id) join app_user u using(user_id) where u.user_login=?",login("xlsx"))).isEqualTo(1);
    }

    @Test void capacityIsRecheckedAfterPreviewAndConcurrentConfirmationCreatesOnlyOneAccount() throws Exception {
        long schoolClass=id("select class_id from student where student_id=?",studentId);
        String name=jdbc.queryForObject("select class_number||'-'||class_letter from school_class where class_id=?",String.class,schoolClass);
        var file=importCsv("Учень",login("overlimit"),"",name,"2026/2027","");
        var preview=userImports.preview(file,"csv");assertThat(preview.invalid()).isZero();
        jdbc.update("insert into app_user(user_login,user_email,user_password_hash,user_role) select ?||n,?||n||'@example.invalid','test','STUDENT' from generate_series(1,29) n",prefix+"fill",prefix+"fill");
        jdbc.update("insert into student(user_id,class_id,student_name,student_surname,student_birthdate,student_enrollment_date,student_status) select user_id,?,'Іван','Петренко','2011-01-01','2026-09-01','STUDYING' from app_user where user_login like ?",schoolClass,prefix+"fill%");
        assertThatThrownBy(()->userImports.confirm(file,"csv",preview.fingerprint())).hasMessageContaining("Жодного");
        assertThat(id("select count(*) from app_user where user_login=?",login("overlimit"))).isZero();
        String subject="Паралельно "+prefix;jdbc.update("insert into subject(subject_title) values (?)",subject);
        var concurrent=importCsv("Вчитель",login("once"),subject,"","","");
        var ready=userImports.preview(concurrent,"csv");assertThat(ready.invalid()).isZero();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> task=()->{try{userImports.confirm(concurrent,"csv",ready.fingerprint());return true;}catch(com.ediary.backend.common.error.ApplicationException error){return false;}};
            var first=pool.submit(task);var second=pool.submit(task);
            assertThat(List.of(first.get(30,java.util.concurrent.TimeUnit.SECONDS),second.get(30,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(id("select count(*) from app_user where user_login=?",login("once"))).isEqualTo(1);
    }

    private org.springframework.mock.web.MockMultipartFile importCsv(String role,String login,String subject,String schoolClass,String year,String childLogin) throws Exception {
        var out=new java.io.StringWriter();
        try(var csv=new org.apache.commons.csv.CSVPrinter(out,org.apache.commons.csv.CSVFormat.RFC4180.builder().setDelimiter(';').get())) {
            csv.printRecord(com.ediary.backend.user.admin.service.UserImportFileService.COLUMNS);
            csv.printRecord(role,login,"Петренко","Іван","Олександрович",login+"@example.invalid",role.equals("Учень")?"":"+380671112233",role.equals("Учень")?"15.04.2011":"15.04.1980",schoolClass,year,subject,childLogin);
        }
        return new org.springframework.mock.web.MockMultipartFile("file","users.csv","text/csv",out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private org.springframework.mock.web.MockMultipartFile combineCsv(org.springframework.mock.web.MockMultipartFile first,
        org.springframework.mock.web.MockMultipartFile... rest) throws Exception {
        var charset=java.nio.charset.StandardCharsets.UTF_8;
        var text=new StringBuilder(new String(first.getBytes(),charset));
        for (var file : rest) text.append(new String(file.getBytes(),charset).split("\\R",2)[1]);
        return new org.springframework.mock.web.MockMultipartFile("file","users.csv","text/csv",text.toString().getBytes(charset));
    }

    private long linkedStudentId(String parentLogin) {
        return id("select sp.student_id from student_parents sp join parent p using(parent_id) join app_user u using(user_id) where u.user_login=?",parentLogin);
    }

    private String login(String type){return prefix+type;}
    private long userId(String type,String role){return id("insert into app_user(user_login,user_email,user_password_hash,user_role) values (?,?,'test',?::enum_user_role) returning user_id",login(type),login(type)+"@example.invalid",role);}
    private long id(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
}
