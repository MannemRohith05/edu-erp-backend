package com.eduerp.controller;

import com.eduerp.dto.*;
import com.eduerp.entity.*;
import com.eduerp.repository.TeacherRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/payroll") @RequiredArgsConstructor
public class PayrollController {
    private final PayrollService service; private final UserRepository users; private final TeacherRepository teachers;
    @GetMapping("/structures") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public List<SalaryStructureDTO> structures() { return service.allStructures(); }
    @PostMapping("/structures") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public SalaryStructureDTO configure(@RequestBody SalaryStructureDTO dto, @AuthenticationPrincipal UserDetails d) { return service.configure(dto, current(d).getId()); }
    @GetMapping("/structures/me") @PreAuthorize("hasRole('TEACHER')") public List<SalaryStructureDTO> myStructures(@AuthenticationPrincipal UserDetails d) { return service.myStructures(teacher(d).getId()); }
    @GetMapping("/records") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public List<PayrollRecordDTO> records() { return service.allPayroll(); }
    @PostMapping("/records/generate") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public PayrollRecordDTO generate(@RequestParam Long teacherId, @RequestParam Integer year, @RequestParam Integer month, @AuthenticationPrincipal UserDetails d) { return service.generate(teacherId, year, month, current(d).getId()); }
    @PutMapping("/records/{id}") @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')") public PayrollRecordDTO update(@PathVariable Long id, @RequestBody PayrollRecordDTO dto, @AuthenticationPrincipal UserDetails d) { return service.updateStatus(id, dto.getStatus(), dto.getPaymentDate(), dto.getPaymentReference(), dto.getPayslipReference(), current(d).getId()); }
    @GetMapping("/records/me") @PreAuthorize("hasRole('TEACHER')") public List<PayrollRecordDTO> myRecords(@AuthenticationPrincipal UserDetails d) { return service.myPayroll(teacher(d).getId()); }
    private User current(UserDetails d) { return users.findByEmail(d.getUsername()).orElseThrow(); }
    private Teacher teacher(UserDetails d) { return teachers.findByUserId(current(d).getId()).orElseThrow(); }
}
