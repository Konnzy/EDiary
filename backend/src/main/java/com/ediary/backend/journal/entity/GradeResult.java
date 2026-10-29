package com.ediary.backend.journal.entity;

public enum GradeResult {
    PASSED, NOT_PASSED, EXEMPTED;

    public String label() {
        return switch(this) { case PASSED -> "зар."; case NOT_PASSED -> "не зар."; case EXEMPTED -> "зв."; };
    }
}
