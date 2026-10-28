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
    @Autowired UserNotificationService notifications;
    @Autowired NotificationEmailSender sender;
    @Autowired StudentProfileRepository students;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext context;
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
        String fullText="Інструкція: "+"А".repeat(1200)+"\nТермін виконання: 04.11.2026";
        new TransactionTemplate(transactions).executeWithoutResult(s->notifications.forStudent(students.findById(studentId).orElseThrow(),"HOMEWORK","Домашнє завдання оновлено",fullText,prefix+"long"));
        long notice=id("select notification_id from user_notification where recipient_id=? and event_key=?",studentUser,prefix+"long");
        assertThat(jdbc.queryForObject("select length(body) from user_notification where notification_id=?",Integer.class,notice)).isLessThanOrEqualTo(500);
        assertThat(jdbc.queryForObject("select email_body from user_notification where notification_id=?",String.class,notice)).contains(fullText,"Учень:","Клас:");
        sender.deliver(notice);
        verify(email).sendText(anyString(),eq("Домашнє завдання оновлено"),argThat(body->body.contains(fullText)&&body.contains("Переглянути домашнє завдання:")&&body.contains("Дата події:")));
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
    private String login(String type){return prefix+type;}
    private long userId(String type,String role){return id("insert into app_user(user_login,user_email,user_password_hash,user_role) values (?,?,'test',?::enum_user_role) returning user_id",login(type),login(type)+"@example.invalid",role);}
    private long id(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
}
