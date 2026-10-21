package com.eduerp.controller;

import com.eduerp.dto.*;
import com.eduerp.entity.User;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.FeeService;
import com.eduerp.service.PaymentGateway;
import com.eduerp.service.PaymentNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController @RequestMapping("/api/fees") @RequiredArgsConstructor
public class FeeController {
    private final FeeService feeService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentNotificationService paymentNotificationService;
    private final ObjectMapper objectMapper;

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
                                       @AuthenticationPrincipal UserDetails details) {
        return feeService.createInvoice(studentId, structureId, currentUser(details).getId());
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public List<FeePaymentDTO> invoices() { return feeService.getAllInvoices(); }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public FinanceDashboardDTO dashboard() { return feeService.getFinanceDashboard(); }

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
    public FeePaymentDTO initiate(@RequestParam Long invoiceId,
                                  @RequestParam BigDecimal amount,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @AuthenticationPrincipal UserDetails details) {
        return feeService.initiatePayment(invoiceId, amount, idempotencyKey, currentUser(details));
    }

    @PostMapping("/me/payments/confirm")
    @PreAuthorize("hasRole('STUDENT')")
    public FeePaymentDTO confirm(@RequestParam String orderId, @RequestParam String paymentId,
                                 @RequestParam String signature, @AuthenticationPrincipal UserDetails details) {
        User user = currentUser(details);
        return executeWithRetry(() -> feeService.confirmRazorpayPayment(orderId, paymentId, signature, user));
    }

    @PostMapping("/me/payments/reconcile")
    @PreAuthorize("hasRole('STUDENT')")
    public FeePaymentDTO reconcileMyPayment(@RequestParam String orderId, @AuthenticationPrincipal UserDetails details) {
        User user = currentUser(details);
        return executeWithRetry(() -> feeService.reconcileStudentOrder(orderId, user));
    }

    @PostMapping("/payments/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public FeePaymentDTO reconcile(@RequestParam String orderId, @AuthenticationPrincipal UserDetails details) {
        Long adminId = currentUser(details).getId();
        return executeWithRetry(() -> feeService.reconcilePaymentOrder(orderId, adminId));
    }

    @GetMapping("/me/payments/{paymentId}/receipt")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> receipt(@PathVariable String paymentId, @AuthenticationPrincipal UserDetails details) {
        byte[] content = feeService.receipt(paymentId, currentUser(details)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok().header("Content-Type", "text/plain; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=eduerp-receipt-" + paymentId + ".txt").body(content);
    }

    @GetMapping("/me/payments/{paymentId}/receipt.pdf")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<byte[]> pdfReceipt(@PathVariable String paymentId, @AuthenticationPrincipal UserDetails details) {
        return ResponseEntity.ok().header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=eduerp-receipt-" + paymentId + ".pdf")
                .body(feeService.receiptPdf(paymentId, currentUser(details)));
    }

    @GetMapping("/payments/{paymentId}/receipt.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public ResponseEntity<byte[]> adminPdfReceipt(@PathVariable String paymentId, @AuthenticationPrincipal UserDetails details) {
        return ResponseEntity.ok().header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=eduerp-receipt-" + paymentId + ".pdf")
                .body(feeService.receiptPdfForAdministrator(paymentId, currentUser(details)));
    }

    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader("X-Razorpay-Signature") String signature) throws Exception {
        paymentGateway.verifyWebhookSignature(payload, signature);
        JsonNode entity = objectMapper.readTree(payload).path("payload").path("payment").path("entity");
        String eventId = objectMapper.readTree(payload).path("id").asText();
        String orderId = entity.path("order_id").asText();
        String paymentId = entity.path("id").asText();
        String status = entity.path("status").asText();
        BigDecimal amount = BigDecimal.valueOf(entity.path("amount").asLong()).movePointLeft(2);
        executeWithRetry(() -> {
            feeService.handleRazorpayWebhook(eventId, orderId, paymentId, status, amount);
            return null;
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/payments/{paymentId}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public FeePaymentDTO refund(@PathVariable Long paymentId, @RequestParam BigDecimal amount,
                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return feeService.refund(paymentId, amount, idempotencyKey);
    }

    @PostMapping("/notifications/{notificationId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public ResponseEntity<Void> retryNotification(@PathVariable Long notificationId) {
        paymentNotificationService.retry(notificationId);
        return ResponseEntity.noContent().build();
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> action) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (org.springframework.dao.TransientDataAccessException ex) {
                if (attempt == maxAttempts) throw ex;
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Retry failed");
    }

    private User currentUser(UserDetails details) { return userRepository.findByEmail(details.getUsername()).orElseThrow(); }
    private com.eduerp.entity.Student student(UserDetails details) {
        return studentRepository.findByUserId(currentUser(details).getId()).orElseThrow();
    }
}
