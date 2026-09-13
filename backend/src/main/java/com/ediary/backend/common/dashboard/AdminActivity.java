package com.ediary.backend.common.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_activity")
@Getter
@NoArgsConstructor
public class AdminActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Long id;

    @Column(name = "activity_actor_login", nullable = false, length = 100)
    private String actorLogin;

    @Column(name = "activity_title", nullable = false, length = 100)
    private String title;

    @Column(name = "activity_target", nullable = false, length = 255)
    private String target;

    @Column(name = "activity_occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public AdminActivity(String actorLogin, String title, String target, LocalDateTime occurredAt) {
        this.actorLogin = actorLogin;
        this.title = title;
        this.target = target;
        this.occurredAt = occurredAt;
    }
}
