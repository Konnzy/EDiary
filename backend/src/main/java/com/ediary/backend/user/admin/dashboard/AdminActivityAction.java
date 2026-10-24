package com.ediary.backend.user.admin.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.servlet.HandlerMapping;

record AdminActivityAction(String title, String target) {

    static AdminActivityAction from(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String path)) return null;
        String action = request.getMethod() + " " + path;
        return switch (action) {
            case "POST /api/admin/users" -> new AdminActivityAction("Створено користувача", "Обліковий запис");
            case "PATCH /api/admin/users/{userId}/status" -> new AdminActivityAction("Змінено статус користувача", target("Користувач", request, "userId"));
            case "PATCH /api/admin/users/{userId}" -> new AdminActivityAction("Відредаговано користувача", target("Користувач", request, "userId"));
            case "POST /api/admin/users/{userId}/reset-password" -> new AdminActivityAction("Скинуто пароль користувача", target("Користувач", request, "userId"));
            case "DELETE /api/admin/users/{userId}" -> new AdminActivityAction("Видалено користувача", target("Користувач", request, "userId"));
            case "POST /api/admin/registration-requests/{requestId}/approve" -> new AdminActivityAction("Схвалено заявку", target("Заявка", request, "requestId"));
            case "POST /api/admin/registration-requests/{requestId}/reject" -> new AdminActivityAction("Відхилено заявку", target("Заявка", request, "requestId"));
            case "POST /api/admin/classes" -> new AdminActivityAction("Створено клас", "Клас");
            case "PATCH /api/admin/classes/{classId}" -> new AdminActivityAction("Відредаговано клас", target("Клас", request, "classId"));
            case "DELETE /api/admin/classes/{classId}" -> new AdminActivityAction("Видалено клас", target("Клас", request, "classId"));
            case "POST /api/admin/classes/{classId}/students/{studentId}" -> new AdminActivityAction("Додано учня до класу", studentTarget(request));
            case "PATCH /api/admin/classes/{classId}/students/{studentId}/class" -> new AdminActivityAction("Переведено учня", studentTarget(request));
            case "DELETE /api/admin/classes/{classId}/students/{studentId}" -> new AdminActivityAction("Вилучено учня з класу", studentTarget(request));
            case "POST /api/admin/subjects" -> new AdminActivityAction("Створено предмет", "Предмет");
            case "PATCH /api/admin/subjects/{subjectId}" -> new AdminActivityAction("Відредаговано предмет", target("Предмет", request, "subjectId"));
            case "DELETE /api/admin/subjects/{subjectId}" -> new AdminActivityAction("Видалено предмет", target("Предмет", request, "subjectId"));
            case "POST /api/admin/schedules/academic-years" -> new AdminActivityAction("Додано навчальний рік", "Навчальний рік");
            case "PUT /api/admin/schedules" -> new AdminActivityAction("Збережено розклад", "Клас #" + request.getParameter("classId"));
            default -> null;
        };
    }

    private static String studentTarget(HttpServletRequest request) {
        return target("Учень", request, "studentId") + ", " + target("клас", request, "classId");
    }

    private static String target(String label, HttpServletRequest request, String variable) {
        Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object value = attributes instanceof Map<?, ?> values ? values.get(variable) : null;
        return label + " #" + value;
    }
}
