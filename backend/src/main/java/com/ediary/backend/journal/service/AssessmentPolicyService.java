package com.ediary.backend.journal.service;

import com.ediary.backend.common.audit.AuditService;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.GradeResult;
import com.ediary.backend.journal.entity.LessonWorkType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ediary.backend.common.audit.AuditValues.of;

@Service
@RequiredArgsConstructor
public class AssessmentPolicyService {
    public enum Mode { NUMERIC, PASS_FAIL }
    public record Policy(@jakarta.validation.constraints.NotNull Mode mode, boolean allowPassed, boolean allowExempted) { }
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public Policy find(Long subjectId) {
        return jdbc.query("select mode,allow_passed,allow_exempted from subject_assessment_policy where subject_id=?",
            (rs,n)->new Policy(Mode.valueOf(rs.getString(1)),rs.getBoolean(2),rs.getBoolean(3)),subjectId)
            .stream().findFirst().orElse(new Policy(Mode.NUMERIC,false,false));
    }

    @Transactional(readOnly=true)
    public boolean hasResults(Long subjectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from mark m join lesson l on l.lesson_id=m.lesson_id
                join schedule sc on sc.schedule_id=l.schedule_id where sc.subject_id=?)
            """,Boolean.class,subjectId));
    }

    @Transactional
    public void save(Long subjectId, Policy policy) {
        lockSubject(subjectId);
        if(policy==null||policy.mode()==null)throw invalid("Вкажіть систему оцінювання");
        Policy previous=find(subjectId);
        if(previous.equals(policy))return;
        Long incompatible=jdbc.queryForObject("""
            select count(*) from mark m join lesson l on l.lesson_id=m.lesson_id
            join schedule sc on sc.schedule_id=l.schedule_id where sc.subject_id=?
            and ((? <> ?)
                or (m.mark_result='PASSED' and ?='NUMERIC' and not ?)
                or (m.mark_result='EXEMPTED' and not ?))
            """,Long.class,subjectId,previous.mode().name(),policy.mode().name(),policy.mode().name(),policy.allowPassed(),policy.allowExempted());
        if(incompatible>0)throw new ApplicationException(HttpStatus.CONFLICT,"ASSESSMENT_POLICY_HAS_RESULTS",
                "Налаштування несумісні з результатами цього предмета в одному або кількох класах. Історичні результати не змінено.");
        jdbc.update("""
            insert into subject_assessment_policy(subject_id,mode,allow_passed,allow_exempted) values (?,?,?,?)
            on conflict(subject_id) do update set mode=excluded.mode,allow_passed=excluded.allow_passed,allow_exempted=excluded.allow_exempted
            """,subjectId,policy.mode().name(),policy.allowPassed(),policy.allowExempted());
        audit.record("ASSESSMENT_POLICY_UPDATED","ASSESSMENT_POLICY",subjectId,
            of("mode",previous.mode(),"allowPassed",previous.allowPassed(),"allowExempted",previous.allowExempted()),
            of("mode",policy.mode(),"allowPassed",policy.allowPassed(),"allowExempted",policy.allowExempted()));
    }

    public Policy lockAndFind(Long subjectId) {
        lockSubject(subjectId);
        return find(subjectId);
    }

    public static void validate(Policy policy, LessonWorkType type, Integer score, GradeResult result) {
        if(score!=null&&(score<1||score>12))throw invalid("Бал має бути від 1 до 12");
        if(score!=null&&result!=null)throw invalid("Оберіть або бал, або нечисловий результат");
        if(score!=null&&policy.mode()==Mode.PASS_FAIL)throw invalid("Для цього предмета використовується залікова система");
        if(result==null)return;
        if(type!=LessonWorkType.THEMATIC&&type!=LessonWorkType.SEMESTER&&type!=LessonWorkType.YEAR)
            throw invalid("Нечисловий результат доступний лише для підсумкової роботи");
        if(result==GradeResult.EXEMPTED&&!policy.allowExempted())throw invalid("Звільнення не дозволене для цього предмета");
        if(result==GradeResult.PASSED&&policy.mode()!=Mode.PASS_FAIL&&!policy.allowPassed())throw invalid("Зарахування не дозволене для цього предмета");
        if(result==GradeResult.NOT_PASSED&&policy.mode()!=Mode.PASS_FAIL)throw invalid("Незарахування доступне лише в заліковій системі");
    }

    private void lockSubject(Long subjectId) {
        if(jdbc.query("select subject_id from subject where subject_id=? for update",(rs,n)->rs.getLong(1),subjectId).isEmpty())
            throw new ApplicationException(HttpStatus.NOT_FOUND,"SUBJECT_NOT_FOUND","Предмет не знайдено");
    }
    private static ApplicationException invalid(String message) { return new ApplicationException(HttpStatus.BAD_REQUEST,"INVALID_ASSESSMENT_RESULT",message); }
}
