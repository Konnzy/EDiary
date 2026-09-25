package com.ediary.backend.journal.dto;

import java.util.List;

public record JournalOptionsResponse(
    List<String> academicYears,
    String currentAcademicYear,
    List<ClassOption> classes
) {
    public record ClassOption(Long id, String label, List<SubjectOption> subjects) { }
    public record SubjectOption(Long id, String label) { }
}
