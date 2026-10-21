package com.eduerp.service;

import com.eduerp.dto.EmailRequest;
import com.eduerp.entity.*;
import com.eduerp.repository.FeePaymentRepository;
import com.eduerp.repository.PaymentNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationServiceTest {

    @Mock private PaymentNotificationRepository notificationRepository;
    @Mock private FeePaymentRepository paymentRepository;
    @Mock private EmailService emailService;
    @Mock private SmsService smsService;

    @InjectMocks private PaymentNotificationService notificationService;

    @Test
    void recordsPendingNotificationsAndDispatchesWithoutFailingPaymentOnEmailError() {
        ReflectionTestUtils.setField(notificationService, "financeEmail", "finance@eduerp.invalid");

        User studentUser = User.builder().id(22L).firstName("Student").lastName("Demo").email("student@eduerp.invalid").build();
        Student student = Student.builder().id(11L).studentId("ST-11").user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(100L).student(student).category("Tuition")
                .amount(new BigDecimal("1000.00")).adjustment(BigDecimal.ZERO).build();
        FeePayment payment = FeePayment.builder().id(77L).invoice(invoice).amount(new BigDecimal("500.00"))
                .status(FeePaymentStatus.CONFIRMED).providerOrderId("order_1").providerPaymentId("pay_1")
                .confirmedAt(LocalDateTime.now()).mobileNumber("+919999999999").build();

        when(paymentRepository.findByInvoiceIdOrderByPaidAtDesc(100L)).thenReturn(List.of(payment));

        // When recordPending is called, mock saving the notifications with IDs
        PaymentNotification studentNotif = PaymentNotification.builder().id(1L).payment(payment)
                .channel(NotificationChannel.EMAIL).recipient("student@eduerp.invalid").status(NotificationStatus.PENDING).build();
        PaymentNotification financeNotif = PaymentNotification.builder().id(2L).payment(payment)
                .channel(NotificationChannel.EMAIL).recipient("finance@eduerp.invalid").status(NotificationStatus.PENDING).build();
        PaymentNotification smsNotif = PaymentNotification.builder().id(3L).payment(payment)
                .channel(NotificationChannel.SMS).recipient("+919999999999").status(NotificationStatus.PENDING).build();

        when(notificationRepository.findByPaymentIdAndChannelAndRecipient(77L, NotificationChannel.EMAIL, "student@eduerp.invalid"))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByPaymentIdAndChannelAndRecipient(77L, NotificationChannel.EMAIL, "finance@eduerp.invalid"))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByPaymentIdAndChannelAndRecipient(77L, NotificationChannel.SMS, "+919999999999"))
                .thenReturn(Optional.empty());

        when(notificationRepository.save(any(PaymentNotification.class))).thenAnswer(invocation -> {
            PaymentNotification n = invocation.getArgument(0);
            if (n.getId() != null) return n;
            if (n.getChannel() == NotificationChannel.EMAIL && "student@eduerp.invalid".equals(n.getRecipient())) {
                n.setId(1L);
                return n;
            }
            if (n.getChannel() == NotificationChannel.EMAIL && "finance@eduerp.invalid".equals(n.getRecipient())) {
                n.setId(2L);
                return n;
            }
            if (n.getChannel() == NotificationChannel.SMS) {
                n.setId(3L);
                return n;
            }
            n.setId(99L);
            return n;
        });

        when(notificationRepository.findById(1L)).thenAnswer(inv -> Optional.of(studentNotif));
        when(notificationRepository.findById(2L)).thenAnswer(inv -> Optional.of(financeNotif));
        when(notificationRepository.findById(3L)).thenAnswer(inv -> Optional.of(smsNotif));

        // Simulate email failure for student, success for finance, failure for SMS
        doThrow(new RuntimeException("SMTP connect timeout")).when(emailService).sendEmail(argThat(req -> "student@eduerp.invalid".equals(req.getTo())));
        doNothing().when(emailService).sendEmail(argThat(req -> "finance@eduerp.invalid".equals(req.getTo())));
        doThrow(new RuntimeException("Twilio service unavailable")).when(smsService).sendPaymentConfirmation(any(), any(), any(), any());

        // Call notifyVerifiedPayment outside of active transaction (executes dispatchAll synchronously)
        assertDoesNotThrow(() -> notificationService.notifyVerifiedPayment(payment));

        // Verify status updates on notifications
        assertEquals(NotificationStatus.FAILED, studentNotif.getStatus());
        assertTrue(studentNotif.getLastError().contains("SMTP connect timeout"));

        assertEquals(NotificationStatus.SENT, financeNotif.getStatus());
        assertNull(financeNotif.getLastError());

        assertEquals(NotificationStatus.FAILED, smsNotif.getStatus());
        assertTrue(smsNotif.getLastError().contains("Twilio service unavailable"));
    }
}
