package com.ediary.backend.user.admin.dto;

import java.util.List;
import java.util.Map;

public final class UserImportDtos {
    private UserImportDtos() {}
    public record RawRow(int number, List<String> values) {}
    public record Row(int number, List<String> values, Map<String,String> errors) {}
    public record Preview(String fingerprint, List<String> columns, List<Row> rows, int total, int valid, int invalid) {}
    public record Result(int created, String message) {}
}
