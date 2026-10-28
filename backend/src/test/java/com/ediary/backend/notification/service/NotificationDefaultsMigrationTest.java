package com.ediary.backend.notification.service;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "EDIARY_RUN_DB_TESTS", matches = "true")
class NotificationDefaultsMigrationTest {
    @Test
    void upgradePreservesEmailChoicesAndDismissalsButEnablesInAppAndNewDefaults() throws Exception {
        String schema = "notification_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String baseUrl = System.getenv().getOrDefault("EDIARY_DB_URL", "jdbc:postgresql://localhost:5432/ediary");
        String user = System.getenv().getOrDefault("EDIARY_DB_USERNAME", "ediary");
        String password = System.getenv().getOrDefault("EDIARY_DB_PASSWORD", "ediary");
        String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try {
            Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("26").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("insert into app_user(user_login,user_email,user_password_hash,user_role) values ('legacy','legacy@example.invalid','test','STUDENT')");
                statement.execute("insert into user_notification_settings(user_id,grade_email_enabled,attendance_email_enabled,message_email_enabled) select user_id,true,true,false from app_user where user_login='legacy'");
                statement.execute("insert into notification_in_app_preference(settings_id,preference_key,enabled) select settings_id,'absence',false from user_notification_settings");
                statement.execute("insert into user_notification(recipient_id,category,title,body,target,visible_in_app,dismissed_at,withdrawn) select user_id,'ABSENCE','Test','Test','test',false,flags.dismissed_at,flags.withdrawn from app_user cross join (values (null::timestamp,false),(now()::timestamp,false),(null::timestamp,true)) as flags(dismissed_at,withdrawn)");
            }
            Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("27").load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("select grade_email_enabled,attendance_email_enabled,message_email_enabled from user_notification_settings")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                    assertThat(result.getBoolean(2)).isTrue();
                    assertThat(result.getBoolean(3)).isFalse();
                }
                try (var result = statement.executeQuery("select count(*) from user_notification where visible_in_app and dismissed_at is null and not withdrawn")) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
                statement.execute("insert into app_user(user_login,user_email,user_password_hash,user_role) values ('new','new@example.invalid','test','STUDENT')");
                statement.execute("insert into user_notification_settings(user_id) select user_id from app_user where user_login='new'");
                try (var result = statement.executeQuery("select grade_email_enabled or homework_email_enabled or homework_deadline_reminder_email_enabled or attendance_email_enabled or homework_status_email_enabled or message_email_enabled or schedule_change_email_enabled or class_schedule_change_email_enabled or prolonged_absence_email_enabled or class_roster_change_email_enabled from user_notification_settings join app_user using(user_id) where user_login='new'")) {
                    result.next();
                    assertThat(result.getBoolean(1)).isFalse();
                }
            }
        } finally {
            if (!schema.matches("notification_upgrade_[a-f0-9]{32}")) throw new IllegalStateException("Invalid test schema");
            try (var connection = DriverManager.getConnection(baseUrl, user, password); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
