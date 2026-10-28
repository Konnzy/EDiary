package com.ediary.backend.notification.controller;

import com.ediary.backend.notification.dto.MessageDtos.*;
import com.ediary.backend.notification.service.MessageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@PreAuthorize("hasAnyRole('STUDENT','PARENT','TEACHER')")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService service;
    @GetMapping public Mailbox list(Authentication auth,@RequestParam(defaultValue="inbox") String folder,@RequestParam(defaultValue="0") int page) { return service.list(auth.getName(),folder,page); }
    @GetMapping("/recipients") public List<Recipient> recipients(Authentication auth,@RequestParam(defaultValue="") String query,@RequestParam(required=false) Long id) { return service.recipients(auth.getName(),query,id); }
    @PostMapping public Item send(Authentication auth,@Valid @RequestBody Send request) { return service.send(auth.getName(),request); }
    @PostMapping("/{id}/open") public Item open(Authentication auth,@PathVariable Long id) { return service.open(auth.getName(),id); }
    @PutMapping("/{id}/important") public Item important(Authentication auth,@PathVariable Long id,@Valid @RequestBody Importance request) { return service.important(auth.getName(),id,request.important()); }
}
