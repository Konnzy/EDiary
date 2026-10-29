package com.ediary.backend.notification.service;

import com.ediary.backend.notification.dto.MessageDtos;
import com.ediary.backend.notification.repository.MessageRepository;
import com.ediary.backend.user.repository.StudentProfileRepository;
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
        long schoolClass=id("insert into school_class(class_number,class_letter,class_academic_year,class_teacher_id) values (?,'А','2026/2027',?) returning class_id",jdbc.queryForObject("select count(*)+1 from school_class",Integer.class),teacher);
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
        verify(email,times(5)).sendText(anyString(),anyString(),contains("/#student/"));
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

    private String login(String type){return prefix+type;}
    private long userId(String type,String role){return id("insert into app_user(user_login,user_email,user_password_hash,user_role) values (?,?,'test',?::enum_user_role) returning user_id",login(type),login(type)+"@example.invalid",role);}
    private long id(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
}
