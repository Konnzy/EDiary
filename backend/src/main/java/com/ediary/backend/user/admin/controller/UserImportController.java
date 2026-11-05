package com.ediary.backend.user.admin.controller;

import com.ediary.backend.user.admin.dto.UserImportDtos.*;
import com.ediary.backend.user.admin.service.UserImportFileService;
import com.ediary.backend.user.admin.service.UserImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/users/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserImportController {
    private final UserImportService imports;
    private final UserImportFileService files;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String format) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=users-template."+safeFormat(format))
            .contentType(MediaType.parseMediaType(format.equals("csv")?"text/csv;charset=UTF-8":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(files.template(format));
    }
    private String safeFormat(String format) {
        if (!format.equals("csv")&&!format.equals("xlsx")) throw new com.ediary.backend.common.error.ApplicationException(HttpStatus.BAD_REQUEST,"INVALID_IMPORT_FORMAT","Оберіть CSV або XLSX.");
        return format;
    }
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@RequestParam MultipartFile file,@RequestParam String format) { return imports.preview(file,format); }

    @PostMapping(value="/confirm",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result confirm(@RequestParam MultipartFile file,@RequestParam String format,@RequestParam String fingerprint) {
        return imports.confirm(file,format,fingerprint);
    }
}
