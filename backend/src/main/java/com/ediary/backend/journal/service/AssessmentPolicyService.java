package com.ediary.backend.journal.service;

import com.ediary.backend.common.audit.AuditService;
import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.GradeResult;
import com.ediary.backend.journal.entity.LessonWorkType;
import com.ediary.backend.journal.entity.SubjectAssessmentPolicy;
import com.ediary.backend.journal.repository.GradeRepository;
import com.ediary.backend.journal.repository.SubjectAssessmentPolicyRepository;
import com.ediary.backend.schedule.entity.Subject;
import com.ediary.backend.schedule.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ediary.backend.common.audit.AuditValues.of;

@Service
@RequiredArgsConstructor
public class AssessmentPolicyService {
    public enum Mode { NUMERIC, PASS_FAIL }
    public record Policy(@jakarta.validation.constraints.NotNull Mode mode, boolean allowPassed, boolean allowExempted) { }
    private static final Policy DEFAULT_POLICY = new Policy(Mode.NUMERIC, false, false);
    private final SubjectAssessmentPolicyRepository policies;
    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final AuditService audit;

    public Policy find(Long subjectId) {
        return policies.findById(subjectId).map(this::toPolicy).orElse(DEFAULT_POLICY);
    }

    @Transactional(readOnly=true)
    public boolean hasResults(Long subjectId) {
        return grades.existsByLesson_ScheduleEntry_Subject_Id(subjectId);
    }

    @Transactional
    public void save(Long subjectId, Policy policy) {
        Subject subject = lockSubject(subjectId);
        if(policy==null||policy.mode()==null)throw invalid("Вкажіть систему оцінювання");
        var existing = policies.findById(subjectId);
        Policy previous = existing.map(this::toPolicy).orElse(DEFAULT_POLICY);
        if(previous.equals(policy))return;
        boolean incompatible = (previous.mode() != policy.mode() && hasResults(subjectId))
            || (policy.mode() == Mode.NUMERIC && !policy.allowPassed()
                && grades.existsByLesson_ScheduleEntry_Subject_IdAndResult(subjectId, GradeResult.PASSED))
            || (!policy.allowExempted() && grades.existsByLesson_ScheduleEntry_Subject_IdAndResult(subjectId, GradeResult.EXEMPTED));
        if(incompatible)throw new ApplicationException(HttpStatus.CONFLICT,"ASSESSMENT_POLICY_HAS_RESULTS",
                "Налаштування несумісні з результатами цього предмета в одному або кількох класах. Історичні результати не змінено.");
        var entity = existing.orElseGet(() -> new SubjectAssessmentPolicy(subject));
        entity.update(policy.mode(), policy.allowPassed(), policy.allowExempted());
        policies.save(entity);
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

    private Policy toPolicy(SubjectAssessmentPolicy entity) {
        return new Policy(entity.getMode(), entity.isAllowPassed(), entity.isAllowExempted());
    }

    private Subject lockSubject(Long subjectId) {
        return subjects.findByIdForUpdate(subjectId).orElseThrow(() ->
            new ApplicationException(HttpStatus.NOT_FOUND,"SUBJECT_NOT_FOUND","Предмет не знайдено"));
    }
    private static ApplicationException invalid(String message) { return new ApplicationException(HttpStatus.BAD_REQUEST,"INVALID_ASSESSMENT_RESULT",message); }
}
