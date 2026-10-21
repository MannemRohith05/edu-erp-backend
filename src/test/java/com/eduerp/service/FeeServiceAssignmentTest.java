package com.eduerp.service;

import com.eduerp.entity.FeeInvoice;
import com.eduerp.entity.FeePayment;
import com.eduerp.entity.FeePaymentStatus;
import com.eduerp.entity.FeeStructure;
import com.eduerp.entity.Role;
import com.eduerp.entity.Student;
import com.eduerp.entity.User;
import com.eduerp.exception.ConflictException;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.CourseRepository;
import com.eduerp.repository.FeeInvoiceRepository;
import com.eduerp.repository.FeePaymentRepository;
import com.eduerp.repository.PaymentWebhookEventRepository;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.repository.FeeStructureRepository;
import com.eduerp.dto.FeePaymentDTO;
import com.eduerp.service.TaxRuleService.TaxCalculation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeServiceAssignmentTest {
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
    @InjectMocks private FeeService feeService;

    @Test
    void assignsTheDatabaseFeeStructureAmountAndIgnoresBrowserAmount() {
        User admin = User.builder().id(7L).role(Role.ADMIN).firstName("A").lastName("Admin").email("admin@test.invalid").password("x").build();
        Student student = Student.builder().id(11L).studentId("ST-11").user(User.builder().id(22L).firstName("S").lastName("Student").build()).build();
        FeeStructure structure = FeeStructure.builder().id(31L).category("Bus").amount(new BigDecimal("37000.00")).dueDate(LocalDate.now().plusDays(30)).createdBy(admin).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(studentRepository.findById(11L)).thenReturn(Optional.of(student));
        when(structureRepository.findById(31L)).thenReturn(Optional.of(structure));
        when(invoiceRepository.existsByStudentIdAndFeeStructureId(11L, 31L)).thenReturn(false);
        when(taxRuleService.calculate(eq("Bus"), eq(new BigDecimal("37000.00")), any(LocalDate.class)))
                .thenReturn(new TaxCalculation(new BigDecimal("37000.00"), BigDecimal.ZERO, new BigDecimal("37000.00"), List.of()));
        when(invoiceRepository.save(any(FeeInvoice.class))).thenAnswer(invocation -> {
            FeeInvoice invoice = invocation.getArgument(0);
            invoice.setId(100L);
            return invoice;
        });
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of());

        var result = feeService.createInvoice(11L, 31L, 7L);

        ArgumentCaptor<FeeInvoice> captor = ArgumentCaptor.forClass(FeeInvoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals(new BigDecimal("37000.00"), captor.getValue().getAmount());
        assertEquals(new BigDecimal("37000.00"), result.getAmount());
        assertEquals(BigDecimal.ZERO, result.getPaid());
        assertEquals(new BigDecimal("37000.00"), result.getBalance());
        org.junit.jupiter.api.Assertions.assertNull(result.getStatus());
        verify(taxRuleService).calculate(eq("Bus"), eq(new BigDecimal("37000.00")), any(LocalDate.class));
    }

    @Test
    void rejectsZeroValueFeeStructureBeforeSavingInvoice() {
        User admin = User.builder().id(7L).role(Role.ADMIN).build();
        Student student = Student.builder().id(11L).user(User.builder().id(22L).firstName("S").lastName("Student").build()).build();
        FeeStructure structure = FeeStructure.builder().id(31L).category("Bus").amount(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(studentRepository.findById(11L)).thenReturn(Optional.of(student));
        when(structureRepository.findById(31L)).thenReturn(Optional.of(structure));
        when(invoiceRepository.existsByStudentIdAndFeeStructureId(11L, 31L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> feeService.createInvoice(11L, 31L, 7L));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void initiatesBusPaymentFromThePersistedInvoiceBalance() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).firstName("S").lastName("Student").build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Bus")
                .amount(new BigDecimal("37000.00")).subtotal(new BigDecimal("37000.00"))
                .taxTotal(BigDecimal.ZERO).adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of());
        when(paymentGateway.createOrder(eq(100L), eq(new BigDecimal("37000.00")), eq("bus-idem"), eq("EDUERP-INV-100")))
                .thenReturn(new PaymentGateway.PaymentOrder("RAZORPAY", "order_bus", "rzp_test_key", new BigDecimal("37000.00"), "INR"));

        FeePaymentDTO result = feeService.initiatePayment(100L, new BigDecimal("37000.00"), "bus-idem", studentUser);

        verify(paymentGateway).createOrder(100L, new BigDecimal("37000.00"), "bus-idem", "EDUERP-INV-100");
        assertEquals("rzp_test_key", result.getProviderKeyId());
        assertEquals(new BigDecimal("37000.00"), result.getProviderAmount());
        assertEquals("INR", result.getProviderCurrency());
    }

    @Test
    void reusesAnExistingPendingOrderForTheSameAmountInsteadOfCreatingAnotherOrder() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).firstName("S").lastName("Student").build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment pending = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-old").idempotencyKey("old-idem").providerOrderId("order_old")
                .status(FeePaymentStatus.PENDING).build();
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKey("new-idem")).thenReturn(Optional.empty());
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(pending));
        when(paymentGateway.publicKeyId()).thenReturn("rzp_test_key");
        when(paymentGateway.checkOrder("order_old", new BigDecimal("500.00")))
                .thenReturn(new PaymentGateway.OrderCheck(true, true));

        FeePaymentDTO result = feeService.initiatePayment(100L, new BigDecimal("500.00"), "new-idem", studentUser);

        assertEquals("order_old", result.getProviderOrderId());
        verify(paymentGateway, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void replacesADefinitivelyInvalidPendingOrderWithAFreshProviderOrder() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment stale = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-stale").idempotencyKey("stale-idem").providerOrderId("order_stale")
                .status(FeePaymentStatus.PENDING).build();
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByIdempotencyKey("fresh-idem")).thenReturn(Optional.empty());
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(stale));
        when(paymentGateway.checkOrder("order_stale", new BigDecimal("500.00")))
                .thenReturn(new PaymentGateway.OrderCheck(false, true));
        when(paymentGateway.createOrder(eq(100L), eq(new BigDecimal("500.00")), eq("fresh-idem"), eq("EDUERP-INV-100")))
                .thenReturn(new PaymentGateway.PaymentOrder("RAZORPAY", "order_fresh", "rzp_test_key", new BigDecimal("500.00"), "INR"));

        FeePaymentDTO result = feeService.initiatePayment(100L, new BigDecimal("500.00"), "fresh-idem", studentUser);

        assertEquals(FeePaymentStatus.FAILED, stale.getStatus());
        assertEquals("PROVIDER_ORDER_INVALID", stale.getFailureCode());
        assertEquals(new BigDecimal("500.00"), result.getProviderAmount());
        verify(paymentGateway).createOrder(100L, new BigDecimal("500.00"), "fresh-idem", "EDUERP-INV-100");
    }

    @Test
    void rejectsDuplicateAssignmentWithoutSavingAnotherInvoice() {
        User admin = User.builder().id(7L).role(Role.ADMIN).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(studentRepository.findById(11L)).thenReturn(Optional.of(Student.builder().id(11L).build()));
        when(structureRepository.findById(31L)).thenReturn(Optional.of(FeeStructure.builder().id(31L).build()));
        when(invoiceRepository.existsByStudentIdAndFeeStructureId(11L, 31L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> feeService.createInvoice(11L, 31L, 7L));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void rejectsNonAdministratorAssignment() {
        User student = User.builder().id(7L).role(Role.STUDENT).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> feeService.createInvoice(11L, 31L, 7L));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidAmountAndInvoiceBeforeCallingRazorpay() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        assertThrows(IllegalArgumentException.class,
                () -> feeService.initiatePayment(100L, BigDecimal.ZERO, "invalid-amount", studentUser));

        when(invoiceRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> feeService.initiatePayment(999L, new BigDecimal("500.00"), "missing-invoice", studentUser));
        verify(paymentGateway, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void allowsPartialPaymentWithinTheLatestOutstandingBalance() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00"))
                .taxTotal(BigDecimal.ZERO).adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of());
        when(paymentGateway.createOrder(eq(100L), eq(new BigDecimal("200.00")), eq("partial-idem"), eq("EDUERP-INV-100")))
                .thenReturn(new PaymentGateway.PaymentOrder("RAZORPAY", "order_partial", "rzp_test_key", new BigDecimal("200.00"), "INR"));

        FeePaymentDTO result = feeService.initiatePayment(100L, new BigDecimal("200.00"), "partial-idem", studentUser);

        assertEquals(new BigDecimal("200.00"), result.getProviderAmount());
    }

    @Test
    void rejectsPaymentAboveOutstandingBalanceBeforeCreatingProviderOrder() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00"))
                .taxTotal(BigDecimal.ZERO).adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> feeService.initiatePayment(100L, new BigDecimal("600.00"), "overbalance-idem", studentUser));
        verify(paymentGateway, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void confirmsPartialPaymentOnceAndReturnsAuthoritativeBalance() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment payment = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("200.00"))
                .reference("PENDING-order").idempotencyKey("idem").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();
        org.mockito.Mockito.lenient().when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderPaymentId("pay_1")).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        FeePaymentDTO result = feeService.confirmRazorpayPayment("order_1", "pay_1", "signature", studentUser);

        assertEquals(FeePaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals("pay_1", payment.getProviderPaymentId());
        assertEquals(new BigDecimal("200.00"), result.getPaid());
        assertEquals(new BigDecimal("300.00"), result.getBalance());
    }

    @Test
    void confirmsFiveHundredRupeesAgainstFiveHundredRupeesOutstanding() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment payment = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-order").idempotencyKey("idem").providerOrderId("order_500")
                .status(FeePaymentStatus.PENDING).build();
        when(paymentRepository.findByProviderOrderIdForUpdate("order_500")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByProviderPaymentId("pay_500")).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        FeePaymentDTO result = feeService.confirmRazorpayPayment("order_500", "pay_500", "signature", studentUser);

        assertEquals(FeePaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals(new BigDecimal("500.00"), result.getPaid());
        assertEquals(0, result.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void rejectsAConcurrentInitiationWhenTheEntireBalanceIsAlreadyReserved() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment reserved = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("PENDING-other").idempotencyKey("other-idem").providerOrderId("order_reserved")
                .status(FeePaymentStatus.PENDING).build();
        when(invoiceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(reserved));

        assertThrows(IllegalArgumentException.class,
                () -> feeService.initiatePayment(100L, new BigDecimal("200.00"), "new-idem", studentUser));
        verify(paymentGateway, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidSignatureWithoutChangingPaymentState() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment payment = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("200.00"))
                .reference("PENDING-order").idempotencyKey("idem").providerOrderId("order_1")
                .status(FeePaymentStatus.PENDING).build();
        org.mockito.Mockito.lenient().when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        doThrow(new IllegalArgumentException("Invalid payment signature"))
                .when(paymentGateway).verifyPaymentSignature("order_1", "pay_1", "bad");

        assertThrows(IllegalArgumentException.class,
                () -> feeService.confirmRazorpayPayment("order_1", "pay_1", "bad", studentUser));
        assertEquals(FeePaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any(FeePayment.class));
    }

    @Test
    void duplicateConfirmedCallbackReturnsTheCurrentLedgerWithoutCountingAgain() {
        User studentUser = User.builder().id(22L).role(Role.STUDENT).build();
        Student student = Student.builder().id(11L).user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).subtotal(new BigDecimal("500.00")).taxTotal(BigDecimal.ZERO)
                .adjustment(BigDecimal.ZERO).dueDate(LocalDate.now().plusDays(30)).build();
        FeePayment payment = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("500.00"))
                .reference("pay_1").idempotencyKey("idem").providerOrderId("order_1").providerPaymentId("pay_1")
                .status(FeePaymentStatus.CONFIRMED).build();
        org.mockito.Mockito.lenient().when(paymentRepository.findByProviderOrderIdForUpdate("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        FeePaymentDTO result = feeService.confirmRazorpayPayment("order_1", "pay_1", "signature", studentUser);

        assertEquals(new BigDecimal("500.00"), result.getPaid());
        assertEquals(0, result.getBalance().compareTo(BigDecimal.ZERO));
        verify(paymentRepository, never()).save(any(FeePayment.class));
    }

    @Test
    void ignoresAnAlreadyProcessedWebhookEvent() {
        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", "evt_1")).thenReturn(true);

        feeService.handleRazorpayWebhook("evt_1", "order_1", "pay_1", "captured", new BigDecimal("200.00"));

        verify(paymentRepository, never()).findByProviderOrderId(any());
        verify(webhookEventRepository, never()).save(any());
    }
}
