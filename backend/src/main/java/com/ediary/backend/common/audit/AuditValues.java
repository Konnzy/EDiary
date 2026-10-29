package com.ediary.backend.common.audit;

import java.util.*;

public final class AuditValues {
    private static final Set<String> FIELDS = Set.of("score", "attendance", "homeworkStatus", "workType", "topic", "homework",
        "deadline", "status", "role", "studentId", "lessonId", "classId", "subjectId", "teacherId", "academicYear", "week",
        "lessons", "day", "number", "room", "start", "end", "created", "deleted", "passwordChanged", "loginChanged",
        "emailChanged", "profileChanged", "notificationPreferencesChanged", "phoneChanged", "avatarChanged", "linked", "commentChanged",
        "teacherIds", "classNumber", "classLetter", "processedUserId", "count", "reasonCode", "active", "mustChangePassword", "title",
        "result", "mode", "allowPassed", "allowExempted");
    private AuditValues() {}

    public static Map<String, Object> of(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Audit fields must be key/value pairs");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            String key = (String) pairs[index];
            if (!FIELDS.contains(key)) throw new IllegalArgumentException("Unsupported audit field");
            result.put(key, pairs[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }

    static Object validate(Object value, int depth) {
        if (depth > 5) throw new IllegalArgumentException("Audit value is too deeply nested");
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Enum<?> item) return item.name();
        if (value instanceof java.time.temporal.TemporalAccessor) return value.toString();
        if (value instanceof String text && text.length() <= 2000) return text;
        if (value instanceof Collection<?> items && items.size() <= 1000) return items.stream().map(item -> validate(item, depth + 1)).toList();
        if (value instanceof Map<?, ?> items && items.size() <= FIELDS.size()) {
            Map<String, Object> result = new LinkedHashMap<>();
            items.forEach((key, item) -> {
                if (!(key instanceof String field) || !FIELDS.contains(field)) throw new IllegalArgumentException("Unsupported audit field");
                result.put(field, validate(item, depth + 1));
            });
            return result;
        }
        throw new IllegalArgumentException("Unsupported audit value");
    }
}
