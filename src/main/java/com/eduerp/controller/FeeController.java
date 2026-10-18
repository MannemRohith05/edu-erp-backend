package com.eduerp.controller;

import com.eduerp.dto.*;
import com.eduerp.entity.User;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.FeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController @RequestMapping("/api/fees") @RequiredArgsConstructor
public class FeeController {
    private final FeeService feeService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    @GetMapping("/structures")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public List<FeeStructureDTO> structures() { return feeService.getStructures(); }

    @PostMapping("/structures")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public FeeStructureDTO createStructure(@RequestBody FeeStructureDTO dto, @AuthenticationPrincipal UserDetails details) {
        return feeService.createStructure(dto, currentUser(details).getId());
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public FeePaymentDTO createInvoice(@RequestParam Long studentId, @RequestParam Long structureId,
                                       @RequestParam BigDecimal amount, @AuthenticationPrincipal UserDetails details) {
        return feeService.createInvoice(studentId, structureId, amount, currentUser(details).getId());
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public List<FeePaymentDTO> invoices() { return feeService.getAllInvoices(); }

    @PostMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public FeePaymentDTO payment(@PathVariable Long invoiceId, @RequestParam BigDecimal amount,
                                 @RequestParam String reference, @RequestParam(required = false) String notes,
                                 @AuthenticationPrincipal UserDetails details) {
        return feeService.recordPayment(invoiceId, amount, reference, notes, currentUser(details).getId());
    }

    @GetMapping("/me/structures")
    @PreAuthorize("hasRole('STUDENT')")
    public List<FeeStructureDTO> myStructures(@AuthenticationPrincipal UserDetails details) {
        return feeService.getApplicableStructures(student(details).getId());
    }

    @GetMapping("/me/ledger")
    @PreAuthorize("hasRole('STUDENT')")
    public List<FeePaymentDTO> myLedger(@AuthenticationPrincipal UserDetails details) {
        return feeService.getLedger(student(details).getId());
    }

    @PostMapping("/me/payments/initiate")
    @PreAuthorize("hasRole('STUDENT')")
    public FeePaymentDTO initiate(@RequestParam Long invoiceId, @RequestParam BigDecimal amount,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @AuthenticationPrincipal UserDetails details) {
        return feeService.initiatePayment(invoiceId, amount, idempotencyKey, currentUser(details));
    }

    private User currentUser(UserDetails details) { return userRepository.findByEmail(details.getUsername()).orElseThrow(); }
    private com.eduerp.entity.Student student(UserDetails details) {
        return studentRepository.findByUserId(currentUser(details).getId()).orElseThrow();
    }
}
