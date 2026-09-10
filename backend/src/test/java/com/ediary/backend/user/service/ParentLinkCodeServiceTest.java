package com.ediary.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ediary.backend.common.error.ApplicationException;
import com.ediary.backend.user.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentLinkCodeServiceTest {

    private static final String SECRET = "test-parent-link-secret-with-32-chars";

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Test
    void generatesFormattedCodeAndStoresOnlyHashCandidate() {
        when(studentProfileRepository.existsByParentLinkCodeHash(anyString())).thenReturn(false);
        ParentLinkCodeService service = new ParentLinkCodeService(studentProfileRepository, SECRET);

        ParentLinkCodeService.GeneratedParentLinkCode code = service.generateUnique();

        assertThat(code.value()).matches("[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(code.hash()).hasSize(64).doesNotContain(code.value());
        verify(studentProfileRepository).existsByParentLinkCodeHash(code.hash());
    }

    @Test
    void rejectsMalformedCodeWithoutDatabaseLookup() {
        ParentLinkCodeService service = new ParentLinkCodeService(studentProfileRepository, SECRET);

        assertThatThrownBy(() -> service.requireActiveStudent("wrong-code"))
            .isInstanceOf(ApplicationException.class)
            .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                .isEqualTo("INVALID_PARENT_LINK_CODE"));

        verifyNoInteractions(studentProfileRepository);
    }
}
