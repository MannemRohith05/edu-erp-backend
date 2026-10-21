package com.eduerp.service;

import com.eduerp.dto.EmailRequest;
import com.eduerp.entity.FeeInvoice;
import com.eduerp.entity.FeePayment;
import com.eduerp.entity.FeePaymentStatus;
import com.eduerp.entity.NotificationChannel;
import com.eduerp.entity.NotificationStatus;
import com.eduerp.entity.PaymentNotification;
import com.eduerp.repository.FeePaymentRepository;
import com.eduerp.repository.PaymentNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final PaymentNotificationRepository notificationRepository;
    private final FeePaymentRepository paymentRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    @Value("${app.finance.notification-email:}") private String financeEmail;

    /**
     * Records pending notification entries within the caller's payment transaction,
     * and schedules delivery after transaction commit so slow network operations
     * and provider locks do not conflict or cause lock wait timeouts.
     */
    @Transactional
    public void notifyVerifiedPayment(FeePayment payment) {
        if (payment == null || payment.getStatus() != FeePaymentStatus.CONFIRMED) return;
        FeeInvoice invoice = payment.getInvoice();
        BigDecimal paidToDate = paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream()
                .filter(item -> item.getStatus() == FeePaymentStatus.CONFIRMED)
                .map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = invoice.getAmount().add(invoice.getAdjustment()).subtract(paidToDate).max(BigDecimal.ZERO);
        String body = "Student: " + invoice.getStudent().getUser().getFullName() + "\n"
                + "Student ID: " + invoice.getStudent().getStudentId() + "\n"
                + "Invoice ID: " + invoice.getId() + "\n"
                + "Fee category: " + invoice.getCategory() + "\n"
                + "Invoice total: INR " + invoice.getAmount().toPlainString() + "\n"
                + "Amount paid in this transaction: INR " + payment.getAmount().toPlainString() + "\n"
                + "Total paid to date: INR " + paidToDate.toPlainString() + "\n"
                + "Remaining balance: INR " + balance.toPlainString() + "\n"
                + "Payment status: " + payment.getStatus() + "\n"
                + "Payment date: " + payment.getConfirmedAt() + "\n"
                + "Razorpay payment reference: " + payment.getProviderPaymentId() + "\n"
                + "Razorpay order reference: " + payment.getProviderOrderId() + "\n"
                + "Receipt reference: " + payment.getProviderPaymentId();

        List<Long> notificationIds = new ArrayList<>();

        // Student email
        String studentEmail = invoice.getStudent().getUser().getEmail();
        if (studentEmail != null && !studentEmail.isBlank()) {
            PaymentNotification notification = recordPending(payment, NotificationChannel.EMAIL, studentEmail);
            if (notification != null) notificationIds.add(notification.getId());
        }

        // Finance notification email
        if (financeEmail != null && !financeEmail.isBlank() && !financeEmail.equalsIgnoreCase(studentEmail)) {
            PaymentNotification notification = recordPending(payment, NotificationChannel.EMAIL, financeEmail);
            if (notification != null) notificationIds.add(notification.getId());
        }

        // Student SMS if phone number configured
        if (payment.getMobileNumber() != null && !payment.getMobileNumber().isBlank()) {
            PaymentNotification notification = recordPending(payment, NotificationChannel.SMS, payment.getMobileNumber());
            if (notification != null) notificationIds.add(notification.getId());
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAll(notificationIds, body, balance);
                }
            });
        } else {
            dispatchAll(notificationIds, body, balance);
        }
    }

    private PaymentNotification recordPending(FeePayment payment, NotificationChannel channel, String recipient) {
        return notificationRepository.findByPaymentIdAndChannelAndRecipient(payment.getId(), channel, recipient)
                .orElseGet(() -> notificationRepository.save(PaymentNotification.builder()
                        .payment(payment)
                        .channel(channel)
                        .recipient(recipient)
                        .status(NotificationStatus.PENDING)
                        .attempts(0)
                        .build()));
    }

    public void dispatchAll(List<Long> notificationIds, String body, BigDecimal balance) {
        for (Long id : notificationIds) {
            try {
                dispatchSingle(id, body, balance);
            } catch (Exception ex) {
                log.warn("Payment notification dispatch error for id={}: {}", id, ex.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchSingle(Long notificationId, String body, BigDecimal balance) {
        PaymentNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() == NotificationStatus.SENT) return;
        notification.setAttempts(notification.getAttempts() + 1);
        try {
            if (notification.getChannel() == NotificationChannel.EMAIL) {
                emailService.sendEmail(new EmailRequest(notification.getRecipient(), null, "EduERP payment confirmation", body));
            } else if (notification.getChannel() == NotificationChannel.SMS) {
                FeePayment payment = notification.getPayment();
                smsService.sendPaymentConfirmation(notification.getRecipient(), payment.getProviderPaymentId(), payment.getAmount(), balance);
            }
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setLastError(null);
        } catch (Exception ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(safeError(ex.getMessage()));
        }
        notificationRepository.save(notification);
    }

    @Transactional
    public void retry(Long notificationId) {
        PaymentNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (notification.getStatus() == NotificationStatus.SENT) return;
        FeePayment payment = notification.getPayment();
        FeeInvoice invoice = payment.getInvoice();
        BigDecimal paid = paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream()
                .filter(item -> item.getStatus() == FeePaymentStatus.CONFIRMED).map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = invoice.getAmount().add(invoice.getAdjustment()).subtract(paid).max(BigDecimal.ZERO);
        String body = "EduERP payment confirmation. Invoice " + invoice.getId() + ", amount INR " + payment.getAmount().toPlainString() + ", balance INR " + balance.toPlainString() + ", reference " + payment.getProviderPaymentId();
        if (notification.getChannel() == NotificationChannel.EMAIL) sendEmail(notification, body);
        else sendSms(notification, balance);
    }

    private void sendEmail(PaymentNotification notification, String body) {
        try {
            notification.setAttempts(notification.getAttempts() + 1);
            emailService.sendEmail(new EmailRequest(notification.getRecipient(), null, "EduERP payment confirmation", body));
            notification.setStatus(NotificationStatus.SENT); notification.setSentAt(LocalDateTime.now()); notification.setLastError(null);
        } catch (Exception ex) { notification.setStatus(NotificationStatus.FAILED); notification.setLastError(safeError(ex.getMessage())); }
        notificationRepository.save(notification);
    }

    private void sendSms(PaymentNotification notification, BigDecimal balance) {
        try {
            notification.setAttempts(notification.getAttempts() + 1);
            FeePayment payment = notification.getPayment();
            smsService.sendPaymentConfirmation(notification.getRecipient(), payment.getProviderPaymentId(), payment.getAmount(), balance);
            notification.setStatus(NotificationStatus.SENT); notification.setSentAt(LocalDateTime.now()); notification.setLastError(null);
        } catch (Exception ex) { notification.setStatus(NotificationStatus.FAILED); notification.setLastError(safeError(ex.getMessage())); }
        notificationRepository.save(notification);
    }

    private String safeError(String message) {
        if (message == null || message.isBlank()) return "Notification delivery failed";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
