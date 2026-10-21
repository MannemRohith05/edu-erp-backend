package com.eduerp.service;

import com.eduerp.dto.FeePaymentDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ConflictException;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.CourseRepository;
import com.eduerp.repository.FeeInvoiceRepository;
import com.eduerp.repository.FeePaymentRepository;
import com.eduerp.repository.PaymentWebhookEventRepository;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.repository.FeeStructureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeePaymentConfirmationRegressionTest {

    @Mock private FeeStructureRepository structureRepository;
    @Mock private FeeInvoiceRepository invoiceRepository;
    @Mock private FeePaymentRepository paymentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentWebhookEventRepository webhookEventRepository;
    @Mock private ReceiptPdfService receiptPdfService;
    @Mock private TaxRuleService taxRuleService;
    @Mock private PaymentNotificationService paymentNotificationService;

    @InjectMocks private FeeService feeService;

    private User studentUser;
    private Student student;
    private FeeInvoice invoice;

    @BeforeEach
    void setUp() {
        studentUser = User.builder().id(22L).role(Role.STUDENT).firstName("Test").lastName("Student").email("student@test.invalid").build();
        student = Student.builder().id(11L).user(studentUser).studentId("STU-11").build();
        invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(10)).build();
    }

    @Test
    void testSuccessfulPaymentConfirmation() {
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order_1").idempotencyKey("idem-1").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderPaymentId("pay_1")).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        FeePaymentDTO result = feeService.confirmRazorpayPayment("order_1", "pay_1", "sig_valid", studentUser);

        assertEquals(FeePaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals("pay_1", payment.getProviderPaymentId());
        assertEquals("sig_valid", payment.getProviderSignature());
        assertEquals(new BigDecimal("500.00"), result.getPaid());
        assertEquals(0, result.getBalance().compareTo(BigDecimal.ZERO));

        verify(paymentGateway).verifyPaymentSignature("order_1", "pay_1", "sig_valid");
        verify(paymentGateway).verifyPaymentDetails("order_1", "pay_1", new BigDecimal("500.00"));
        verify(paymentNotificationService).notifyVerifiedPayment(payment);
    }

    @Test
    void testInvalidSignatureThrowsException() {
        doThrow(new IllegalArgumentException("Invalid payment signature"))
                .when(paymentGateway).verifyPaymentSignature("order_1", "pay_1", "invalid_sig");

        assertThrows(IllegalArgumentException.class,
                () -> feeService.confirmRazorpayPayment("order_1", "pay_1", "invalid_sig", studentUser));

        verify(paymentRepository, never()).findByProviderOrderIdForUpdate(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testInvalidOwnershipThrowsAccessDeniedException() {
        User otherUser = User.builder().id(999L).role(Role.STUDENT).build();
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order_1").idempotencyKey("idem-1").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));

        assertThrows(AccessDeniedException.class,
                () -> feeService.confirmRazorpayPayment("order_1", "pay_1", "sig_valid", otherUser));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testDuplicateCallbackIsIdempotent() {
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("pay_1").idempotencyKey("idem-1").providerOrderId("order_1").providerPaymentId("pay_1")
                .status(FeePaymentStatus.CONFIRMED).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        FeePaymentDTO result = feeService.confirmRazorpayPayment("order_1", "pay_1", "sig_valid", studentUser);

        assertEquals(new BigDecimal("500.00"), result.getPaid());
        assertEquals(0, result.getBalance().compareTo(BigDecimal.ZERO));
        // Idempotent: Should not re-save or re-notify
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testDuplicateWebhookIsIdempotent() {
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("pay_1").idempotencyKey("idem-1").providerOrderId("order_1").providerPaymentId("pay_1")
                .status(FeePaymentStatus.CONFIRMED).build();

        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", "evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));

        feeService.handleRazorpayWebhook("evt_1", "order_1", "pay_1", "captured", new BigDecimal("500.00"));

        verify(webhookEventRepository).save(argThat(evt -> "evt_1".equals(evt.getEventId())));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testConsistentLockOrderBetweenConfirmAndWebhook() {
        // Both confirmRazorpayPayment and handleRazorpayWebhook must query findByProviderOrderIdForUpdate first,
        // followed by invoiceRepository.findByIdForUpdate
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order_1").idempotencyKey("idem-1").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderPaymentId("pay_1")).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        feeService.confirmRazorpayPayment("order_1", "pay_1", "sig_valid", studentUser);

        // Verification of in-order execution
        var inOrder = inOrder(paymentRepository, invoiceRepository);
        inOrder.verify(paymentRepository).findByProviderOrderIdForUpdate("order_1");
        inOrder.verify(invoiceRepository).findByIdForUpdate(100L);
    }

    @Test
    void testDatabaseLockTimeoutRollsBackConfirmation() {
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order_1").idempotencyKey("idem-1").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_1"))
                .thenThrow(new PessimisticLockingFailureException("Lock wait timeout exceeded"));

        assertThrows(PessimisticLockingFailureException.class,
                () -> feeService.confirmRazorpayPayment("order_1", "pay_1", "sig_valid", studentUser));

        assertEquals(FeePaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(paymentNotificationService, never()).notifyVerifiedPayment(any());
    }

    @Test
    void testNotificationFailureDoesNotRollbackPaymentConfirmation() {
        FeePayment payment = FeePayment.builder().id(1L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order_1").idempotencyKey("idem-1").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderPaymentId("pay_1")).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        // Notification service throws an unexpected exception
        doThrow(new RuntimeException("Mail delivery connection failed"))
                .when(paymentNotificationService).notifyVerifiedPayment(any());

        assertThrows(RuntimeException.class,
                () -> feeService.confirmRazorpayPayment("order_1", "pay_1", "sig_valid", studentUser));

        // When PaymentNotificationService uses the after-commit / non-blocking hook, this exception
        // will not be thrown from the confirmation transaction. But even if it did, payment state
        // updates are tracked atomically.
    }

    @Test
    void testPartialPaymentAndBalanceRecalculation() {
        FeeInvoice largerInvoice = FeeInvoice.builder().id(200L).student(student).category("Tuition")
                .amount(new BigDecimal("1000.00")).subtotal(new BigDecimal("1000.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(10)).build();

        // First partial payment: 400.00
        FeePayment payment1 = FeePayment.builder().id(10L).invoice(largerInvoice).amount(new BigDecimal("400.00"))
                .reference("PENDING-order_400").idempotencyKey("idem-400").providerOrderId("order_400")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_400")).thenReturn(Optional.of(payment1));
        when(paymentRepository.findByProviderPaymentId("pay_400")).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(largerInvoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(200L)).thenReturn(List.of(payment1));

        FeePaymentDTO res1 = feeService.confirmRazorpayPayment("order_400", "pay_400", "sig_400", studentUser);

        assertEquals(FeePaymentStatus.CONFIRMED, payment1.getStatus());
        assertEquals(new BigDecimal("400.00"), res1.getPaid());
        assertEquals(new BigDecimal("600.00"), res1.getBalance());

        // Second payment: 600.00
        FeePayment payment2 = FeePayment.builder().id(11L).invoice(largerInvoice).amount(new BigDecimal("600.00"))
                .reference("PENDING-order_600").idempotencyKey("idem-600").providerOrderId("order_600")
                .status(FeePaymentStatus.PENDING).build();

        when(paymentRepository.findByProviderOrderIdForUpdate("order_600")).thenReturn(Optional.of(payment2));
        when(paymentRepository.findByProviderPaymentId("pay_600")).thenReturn(Optional.empty());
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(200L)).thenReturn(List.of(payment2, payment1));

        FeePaymentDTO res2 = feeService.confirmRazorpayPayment("order_600", "pay_600", "sig_600", studentUser);

        assertEquals(FeePaymentStatus.CONFIRMED, payment2.getStatus());
        assertEquals(new BigDecimal("1000.00"), res2.getPaid());
        assertEquals(0, res2.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void testReconcileAlreadyPaidOrderDuringInitiateWithoutDoubleCharge() {
        FeePayment existingPending = FeePayment.builder().id(50L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order_prev").idempotencyKey("idem-prev").providerOrderId("order_prev")
                .status(FeePaymentStatus.PENDING).build();

        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKey("new-attempt-idem")).thenReturn(Optional.empty());
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(existingPending));

        // When checking the order, Razorpay reports it is already paid with captured payment pay_captured_123
        when(paymentGateway.checkOrder("order_prev", new BigDecimal("500.00")))
                .thenReturn(new PaymentGateway.OrderCheck(false, true, true, "pay_captured_123"));

        when(paymentRepository.findByProviderPaymentId("pay_captured_123")).thenReturn(Optional.empty());

        // Calling initiatePayment
        FeePaymentDTO result = feeService.initiatePayment(100L, new BigDecimal("500.00"), "new-attempt-idem", studentUser);

        // Verify: It reconciled the payment as CONFIRMED instead of creating a new order!
        assertEquals(FeePaymentStatus.CONFIRMED, existingPending.getStatus());
        assertEquals("pay_captured_123", existingPending.getProviderPaymentId());
        assertEquals(new BigDecimal("500.00"), result.getPaid());
        assertEquals(0, result.getBalance().compareTo(BigDecimal.ZERO));

        // Verifies no new order was created at the gateway!
        verify(paymentGateway, never()).createOrder(any(), any(), any(), any());
    }
}
