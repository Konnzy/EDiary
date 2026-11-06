package com.ediary.backend.schedule.controller;

import com.ediary.backend.schedule.dto.response.ScheduleImportDtos.Preview;
import com.ediary.backend.schedule.dto.response.ScheduleImportDtos.Result;
import com.ediary.backend.schedule.service.ScheduleImportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/schedules/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ScheduleImportController {
    private final ScheduleImportService imports;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String format) {
        byte[] template = imports.template(format);
        String type = "csv".equals(format) ? "text/csv;charset=UTF-8"
            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=schedule-template." + format)
            .contentType(MediaType.parseMediaType(type))
            .body(template);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@RequestParam Long classId, @RequestParam String academicYear,
                           @RequestParam MultipartFile file, @RequestParam String format) {
        return imports.preview(classId, academicYear, file, format);
    }

    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result confirm(@RequestParam Long classId, @RequestParam String academicYear,
                          @RequestParam MultipartFile file, @RequestParam String format,
                          @RequestParam String fingerprint,
                          @RequestParam(required = false) List<Integer> keepRows,
                          @RequestParam(required = false) List<Integer> replaceRows) {
        return imports.confirm(classId, academicYear, file, format, fingerprint,
            keepRows == null ? List.of() : keepRows, replaceRows == null ? List.of() : replaceRows);
    }
}
