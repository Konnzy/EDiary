package com.ediary.backend.notification.repository;

import com.ediary.backend.notification.dto.MessageDtos.Recipient;
import com.ediary.backend.user.entity.UserRole;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageDirectory {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String DIRECTORY = """
        select u.user_id, u.user_role::text as role,
            coalesce(nullif(concat_ws(' ',t.teacher_surname,t.teacher_name,t.teacher_patronymic),''),
                     nullif(concat_ws(' ',s.student_surname,s.student_name,s.student_patronymic),''),
                     nullif(concat_ws(' ',p.parent_surname,p.parent_name,p.parent_patronymic),''), u.user_login) as full_name
        from app_user u
        left join teacher t on t.user_id=u.user_id and u.user_role='TEACHER'
        left join student s on s.user_id=u.user_id and u.user_role='STUDENT'
        left join parent p on p.user_id=u.user_id and u.user_role='PARENT'
        """;
    private static final String CONTACTS = """
        with teaching as (
          select distinct s.student_id, s.user_id as student_user, t.user_id as teacher_user,
            coalesce(t.teacher_id=c.class_teacher_id,false) as homeroom
          from student s join school_class c on c.class_id=s.class_id
          join teacher t on t.teacher_id=c.class_teacher_id or exists (
            select 1 from schedule e where e.class_id=c.class_id and e.teacher_id=t.teacher_id
              and e.schedule_active and e.schedule_academic_year=:year)
          where c.class_academic_year=:year and s.student_status='STUDYING'
        ), pairs as (
          select teacher_user as first_id, student_user as second_id,homeroom from teaching
          union select teaching.teacher_user,p.user_id,teaching.homeroom from teaching
            join student_parents sp on sp.student_id=teaching.student_id join parent p on p.parent_id=sp.parent_id
        ), contacts as (
          select second_id as id,false as homeroom from pairs where first_id=:actor
          union select first_id,homeroom from pairs where second_id=:actor
        ), directory as (
        """ + DIRECTORY + """
          where u.user_status='ACTIVE' and u.user_id in (select id from contacts) and u.user_id<>:actor
        ) select directory.*,exists(select 1 from contacts where contacts.id=directory.user_id and contacts.homeroom) as homeroom
          from directory where 1=1
        """;

    public List<Recipient> search(Long actor, String year, String query, Long id) {
        var params = new HashMap<String,Object>();
        params.put("actor", actor); params.put("year", year);
        var sql = new StringBuilder(CONTACTS);
        if (id != null) { sql.append(" and user_id=:id"); params.put("id",id); }
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i=0; i<terms.length && i<10; i++) {
            if (terms[i].isEmpty()) continue;
            sql.append(" and position(:term" + i + " in lower(full_name))>0"); params.put("term"+i,terms[i]);
        }
        sql.append(" order by homeroom desc,full_name,user_id limit 20");
        return jdbc.query(sql.toString(),params,(rs,row)->new Recipient(rs.getLong("user_id"),rs.getString("full_name"),UserRole.valueOf(rs.getString("role")),rs.getBoolean("homeroom")));
    }

    public Recipient identity(Long id) {
        return jdbc.queryForObject(DIRECTORY + " where u.user_id=:id",Map.of("id",id),
            (rs,row)->new Recipient(rs.getLong("user_id"),rs.getString("full_name"),UserRole.valueOf(rs.getString("role"))));
    }
}
