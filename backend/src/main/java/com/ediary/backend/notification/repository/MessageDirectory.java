package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.dto.MessageDtos.Recipient;
import com.ediary.backend.user.entity.StudentStatus;
import com.ediary.backend.user.entity.UserRole;
import com.ediary.backend.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageDirectory {
    private final EntityManager entities;

    private static final String FULL_NAME = """
        case
            when teacher.id is not null then concat(teacher.surname, ' ', teacher.name,
                case when teacher.patronymic is null then '' else concat(' ', teacher.patronymic) end)
            when student.id is not null then concat(student.surname, ' ', student.name,
                case when student.patronymic is null then '' else concat(' ', student.patronymic) end)
            when parent.id is not null then concat(parent.surname, ' ', parent.name,
                case when parent.patronymic is null then '' else concat(' ', parent.patronymic) end)
            else account.login
        end
        """;

    private static final String PROFILES = """
        from AppUser account
        left join TeacherProfile teacher on teacher.user = account and account.role = :teacherRole
        left join StudentProfile student on student.user = account and account.role = :studentRole
        left join ParentProfile parent on parent.user = account and account.role = :parentRole
        """;

    private static final String CLASS_IN_YEAR = "classroom.academicYear.value = :year and pupil.status = :studying";

    private static final String LEARNER_MATCH = """
        (pupil.user.id = %1$s or exists (
            select 1 from StudentParentLink link
            where link.student = pupil and link.parent.user.id = %1$s
        ))
        """;

    private static final String HOMEROOM = """
        exists (
            select 1 from StudentProfile pupil
            join pupil.schoolClass classroom
            join classroom.classTeacher classTeacher
            where %s and classTeacher.user = account and %s
        )
        """.formatted(CLASS_IN_YEAR, LEARNER_MATCH.formatted(":actor"));

    private static final String CAN_CONTACT = """
        exists (
            select 1 from StudentProfile pupil
            join pupil.schoolClass classroom
            join TeacherProfile educator on educator.user.id in (:actor, account.id)
            where %s
              and (classroom.classTeacher = educator or exists (
                  select 1 from ScheduleEntry entry
                  where entry.schoolClass = classroom and entry.teacher = educator
                    and entry.active = true and entry.academicYear = :year
              ))
              and ((educator.user.id = :actor and %s) or (educator.user = account and %s))
        )
        """.formatted(CLASS_IN_YEAR, LEARNER_MATCH.formatted("account.id"), LEARNER_MATCH.formatted(":actor"));

    public List<Recipient> search(Long actor, String year, String query, Long id) {
        var terms = Arrays.stream(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
            .filter(term -> !term.isEmpty()).limit(10).toList();
        var hql = new StringBuilder("select account.id, ").append(FULL_NAME).append(" as fullName, account.role, ")
            .append(HOMEROOM).append(" as homeroom ").append(PROFILES)
            .append(" where account.status = :active and account.id <> :actor and ").append(CAN_CONTACT);
        if (id != null) hql.append(" and account.id = :recipient");
        for (int index = 0; index < terms.size(); index++) {
            hql.append(" and locate(:term").append(index).append(", lower(").append(FULL_NAME).append(")) > 0");
        }
        hql.append(" order by homeroom desc, fullName, account.id");

        var recipients = recipientQuery(hql.toString())
            .setParameter("actor", actor).setParameter("year", year).setParameter("active", UserStatus.ACTIVE)
            .setParameter("studying", StudentStatus.STUDYING)
            .setMaxResults(20);
        if (id != null) recipients.setParameter("recipient", id);
        for (int index = 0; index < terms.size(); index++) recipients.setParameter("term" + index, terms.get(index));
        return recipients.getResultList();
    }

    public Recipient identity(Long id) {
        return recipientQuery("select account.id, " + FULL_NAME + ", account.role, false " + PROFILES + " where account.id = :id")
            .setParameter("id", id)
            .getSingleResult();
    }

    private jakarta.persistence.TypedQuery<Recipient> recipientQuery(String hql) {
        return entities.createQuery(hql, Recipient.class)
            .setParameter("teacherRole", UserRole.TEACHER)
            .setParameter("studentRole", UserRole.STUDENT)
            .setParameter("parentRole", UserRole.PARENT);
    }
}
