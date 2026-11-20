package com.ediary.backend.common.validation;

public final class UkrainianNamePatterns {
    public static final String REQUIRED = "[А-Яа-яІіЇїЄєҐґ]++(?:[ '’-][А-Яа-яІіЇїЄєҐґ]++)*+";
    public static final String OPTIONAL = "(?:" + REQUIRED + ")?";

    private UkrainianNamePatterns() {
    }
}
