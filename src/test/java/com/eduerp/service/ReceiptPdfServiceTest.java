package com.eduerp.service;

import com.eduerp.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptPdfServiceTest {
    private final ReceiptPdfService service = new ReceiptPdfService();

    @Test
    void rendersFeeReceiptAsA4PdfWithVerifiedPaymentDetails() {
        User studentUser = User.builder().firstName("Sam").lastName("Student").build();
        Student student = Student.builder().studentId("ST-1").user(studentUser).build();
        FeeInvoice invoice = FeeInvoice.builder().id(7L).student(student).category("Swimming")
                .amount(new BigDecimal("500.00")).adjustment(BigDecimal.ZERO).dueDate(LocalDate.now()).build();
        FeePayment payment = FeePayment.builder().id(4L).invoice(invoice).amount(new BigDecimal("200.00"))
                .providerPaymentId("pay_test").confirmedAt(LocalDateTime.now()).status(FeePaymentStatus.CONFIRMED).build();

        String pdf = new String(service.renderFeeReceipt(invoice, payment, BigDecimal.ZERO,
                new BigDecimal("200.00"), new BigDecimal("300.00")), java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("FEE PAYMENT RECEIPT"));
        assertTrue(pdf.contains("This payment:"));
        assertTrue(pdf.contains("INR 200.00"));
        assertTrue(pdf.contains("Remaining balance:"));
        assertTrue(pdf.contains("INR 300.00"));
    }

    @Test
    void rendersPaidTeacherPayslipWithPersistedValues() {
        User teacherUser = User.builder().firstName("Tina").lastName("Teacher").build();
        Teacher teacher = Teacher.builder().employeeId("EMP-1").department("Physics").user(teacherUser).build();
        PayrollRecord record = PayrollRecord.builder().teacher(teacher).payrollYear(2026).payrollMonth(10)
                .basicSalary(new BigDecimal("50000.00")).allowances(new BigDecimal("3000.00"))
                .bonuses(new BigDecimal("2000.00")).grossSalary(new BigDecimal("55000.00"))
                .deductions(new BigDecimal("5000.00")).netSalary(new BigDecimal("50000.00"))
                .status(PayrollStatus.PAID).paymentDate(LocalDate.now()).paymentReference("BANK-1").build();

        String pdf = new String(service.renderPayslip(record), java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("TEACHER SALARY PAYSLIP"));
        assertTrue(pdf.contains("Net salary payable:"));
        assertTrue(pdf.contains("INR 50000.00"));
        assertTrue(pdf.contains("Payment reference:"));
        assertTrue(pdf.contains("BANK-1"));
    }
}
