package com.eduerp.controller;

import com.eduerp.dto.CertificateDTO;
import com.eduerp.entity.User;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/certificates") @RequiredArgsConstructor
public class CertificateController {
    private final CertificateService service; private final UserRepository users; private final StudentRepository students;
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public List<CertificateDTO> all() { return service.all(); }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public CertificateDTO issue(@RequestBody CertificateDTO dto, @AuthenticationPrincipal UserDetails d) { return service.issue(dto, current(d).getId()); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public CertificateDTO correct(@PathVariable Long id, @RequestBody CertificateDTO dto, @AuthenticationPrincipal UserDetails d) { return service.correct(id, dto, current(d).getId()); }
    @PostMapping("/{id}/revoke") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public CertificateDTO revoke(@PathVariable Long id, @AuthenticationPrincipal UserDetails d) { return service.revoke(id, current(d).getId()); }
    @GetMapping("/me") @PreAuthorize("hasRole('STUDENT')") public List<CertificateDTO> mine(@AuthenticationPrincipal UserDetails d) { return service.mine(students.findByUserId(current(d).getId()).orElseThrow().getId()); }
    private User current(UserDetails d) { return users.findByEmail(d.getUsername()).orElseThrow(); }
}
