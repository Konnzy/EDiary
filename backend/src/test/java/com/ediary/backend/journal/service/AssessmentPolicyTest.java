package com.ediary.backend.journal.service;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.journal.entity.GradeResult;
import com.ediary.backend.journal.entity.LessonWorkType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.ediary.backend.journal.service.AssessmentPolicyService.*;

class AssessmentPolicyTest {
    @Test void numericDefaultRejectsNonNumericResultsAndAcceptsEmptyAttendanceOnlyCell() {
        var policy=new Policy(Mode.NUMERIC,false,false);
        validate(policy,LessonWorkType.CLASSWORK,null,null);
        validate(policy,LessonWorkType.CLASSWORK,12,null);
        for(var result:GradeResult.values())assertThatThrownBy(()->validate(policy,LessonWorkType.SEMESTER,null,result)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->validate(policy,LessonWorkType.CLASSWORK,0,null)).isInstanceOf(ApplicationException.class);
    }
    @Test void passFailDoesNotImplyExemptionOrNumericScoring() {
        var policy=new Policy(Mode.PASS_FAIL,false,false);
        for(var type:new LessonWorkType[]{LessonWorkType.THEMATIC,LessonWorkType.SEMESTER,LessonWorkType.YEAR}) {
            validate(policy,type,null,GradeResult.PASSED);validate(policy,type,null,GradeResult.NOT_PASSED);
        }
        assertThatThrownBy(()->validate(policy,LessonWorkType.SEMESTER,10,null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->validate(policy,LessonWorkType.SEMESTER,null,GradeResult.EXEMPTED)).isInstanceOf(ApplicationException.class);
    }
    @Test void optionalPassedAndExemptedDoNotEnableNotPassedOrOrdinaryLessonResults() {
        var policy=new Policy(Mode.NUMERIC,true,true);
        validate(policy,LessonWorkType.SEMESTER,null,GradeResult.PASSED);
        validate(policy,LessonWorkType.SEMESTER,null,GradeResult.EXEMPTED);
        assertThatThrownBy(()->validate(policy,LessonWorkType.SEMESTER,null,GradeResult.NOT_PASSED)).isInstanceOf(ApplicationException.class);
        for(var result:GradeResult.values())assertThatThrownBy(()->validate(policy,LessonWorkType.CLASSWORK,null,result)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(()->validate(policy,LessonWorkType.SEMESTER,9,GradeResult.EXEMPTED)).isInstanceOf(ApplicationException.class);
    }
}
