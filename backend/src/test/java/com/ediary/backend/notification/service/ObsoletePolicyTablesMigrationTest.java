package com.ediary.backend.notification.service;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "EDIARY_RUN_DB_TESTS", matches = "true")
class ObsoletePolicyTablesMigrationTest {
    private static final String URL = System.getenv().getOrDefault("EDIARY_DB_URL", "jdbc:postgresql://localhost:5432/ediary");
    private static final String USER = System.getenv().getOrDefault("EDIARY_DB_USERNAME", "ediary");
    private static final String PASSWORD = System.getenv().getOrDefault("EDIARY_DB_PASSWORD", "ediary");

    @Test void upgradeChecksTransferredPoliciesAndPreservesCurrentData() throws Exception {
        withSchema(url -> {
            flyway(url, "32").migrate();
            try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement()) {
                sql.execute("insert into app_user(user_login,user_email,user_password_hash,user_role) values ('legacy','legacy@example.invalid','test','STUDENT')");
                sql.execute("insert into user_notification_settings(user_id,grade_email_enabled,attendance_email_enabled) select user_id,true,false from app_user");
                sql.execute("insert into notification_in_app_preference(settings_id,preference_key,enabled) select settings_id,'absence',false from user_notification_settings");
                sql.execute("insert into user_notification(recipient_id,category,title,body,target) select user_id,'GRADE','Saved notice','Saved body','student/subjects' from app_user");
                sql.execute("insert into school_class(class_number,class_letter,class_academic_year) values (9,'А','2026/2027')");
                sql.execute("insert into subject(subject_title) values ('Physical education')");
                sql.execute("insert into assessment_policy(class_id,subject_id,mode,allow_passed,allow_exempted) select class_id,subject_id,'NUMERIC',true,true from school_class cross join subject");
            }
            assertThatThrownBy(() -> flyway(url, null).migrate()).hasStackTraceContaining("no migrated assessment settings");
            try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("select count(*) from notification_in_app_preference")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                sql.execute("insert into subject_assessment_policy(subject_id,mode,allow_passed,allow_exempted) select subject_id,'NUMERIC',true,true from subject");
            }
            flyway(url, null).migrate();
            assertRemoved(url);
            try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("select grade_email_enabled,attendance_email_enabled from user_notification_settings")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue(); assertThat(rows.getBoolean(2)).isFalse();
                }
                try (var rows = sql.executeQuery("select mode,allow_passed,allow_exempted from subject_assessment_policy")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("NUMERIC");
                    assertThat(rows.getBoolean(2)).isTrue(); assertThat(rows.getBoolean(3)).isTrue();
                }
                try (var rows = sql.executeQuery("select body from user_notification")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Saved body");
                }
            }
        });
    }

    @Test void freshDatabaseAppliesAllHistoricalMigrationsThenRemovesObsoleteTables() throws Exception {
        withSchema(url -> { flyway(url, null).migrate(); assertRemoved(url); });
    }

    private Flyway flyway(String url, String target) {
        String schema = url.substring(url.lastIndexOf("currentSchema=") + "currentSchema=".length());
        var config = Flyway.configure().dataSource(url, USER, PASSWORD).schemas(schema).defaultSchema(schema);
        if (target != null) config.target(target);
        return config.load();
    }

    private void assertRemoved(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, USER, PASSWORD); var sql = connection.createStatement();
             var rows = sql.executeQuery("select to_regclass('assessment_policy'),to_regclass('notification_in_app_preference'),to_regclass('subject_assessment_policy')")) {
            rows.next(); assertThat(rows.getString(1)).isNull(); assertThat(rows.getString(2)).isNull();
            assertThat(rows.getString(3)).isNotNull();
        }
    }

    private void withSchema(Check check) throws Exception {
        String schema = "obsolete_policy_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        try { check.run(url); }
        finally {
            if (!schema.matches("obsolete_policy_test_[a-f0-9]{32}")) throw new IllegalStateException("Invalid test schema");
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @FunctionalInterface private interface Check { void run(String url) throws Exception; }
}
