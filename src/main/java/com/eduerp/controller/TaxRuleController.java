package com.eduerp.controller;

import com.eduerp.dto.TaxRuleDTO;
import com.eduerp.entity.User;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.TaxRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/tax/rules") @RequiredArgsConstructor
public class TaxRuleController {
    private final TaxRuleService service; private final UserRepository users;
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public List<TaxRuleDTO> all() { return service.all(); }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public TaxRuleDTO create(@RequestBody TaxRuleDTO dto, @AuthenticationPrincipal UserDetails details) { User user = users.findByEmail(details.getUsername()).orElseThrow(); return service.create(dto, user.getId()); }
}
