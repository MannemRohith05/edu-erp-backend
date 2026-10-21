package com.eduerp.service;

import com.eduerp.entity.FeeInvoice;
import com.eduerp.entity.FeePayment;
import com.eduerp.entity.PayrollRecord;
import com.eduerp.entity.Student;
import com.eduerp.entity.Teacher;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReceiptPdfService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    public byte[] render(List<String> lines) {
        return document("EduERP DOCUMENT", "SYSTEM GENERATED DOCUMENT", lines);
    }

    public byte[] renderFeeReceipt(FeeInvoice invoice, FeePayment payment, BigDecimal previousPaid,
                                   BigDecimal totalPaid, BigDecimal balance) {
        Student student = invoice.getStudent();
        String academic = invoice.getFeeStructure() == null ? null : invoice.getFeeStructure().getAcademicYear();
        Integer semester = invoice.getFeeStructure() == null ? null : invoice.getFeeStructure().getSemester();
        String course = invoice.getFeeStructure() == null || invoice.getFeeStructure().getCourse() == null
                ? null : invoice.getFeeStructure().getCourse().getCourseName();
        List<String> fields = new ArrayList<>();
        fields.add("Receipt number: " + safe("EDUERP-" + payment.getId()));
        fields.add("Verified payment reference: " + safe(payment.getProviderPaymentId()));
        fields.add("Payment date: " + format(payment.getConfirmedAt()));
        fields.add("Student name: " + safe(student.getUser().getFullName()));
        fields.add("Student ID: " + safe(student.getStudentId()));
        if (course != null) fields.add("Course / program: " + safe(course));
        if (academic != null) fields.add("Academic year: " + safe(academic));
        if (semester != null) fields.add("Semester: " + semester);
        fields.add("Invoice ID: " + invoice.getId());
        fields.add("Fee category: " + safe(invoice.getCategory()));
        fields.add("Invoice total: " + money(invoice.getAmount().add(invoice.getAdjustment())));
        fields.add("Previously paid: " + money(previousPaid));
        fields.add("This payment: " + money(payment.getAmount()));
        fields.add("Total paid to date: " + money(totalPaid));
        fields.add("Remaining balance: " + money(balance));
        fields.add("Payment method: Razorpay / INR");
        fields.add("Payment status: VERIFIED");
        return document("EduERP", "FEE PAYMENT RECEIPT", fields);
    }

    public byte[] renderPayslip(PayrollRecord record) {
        Teacher teacher = record.getTeacher();
        List<String> fields = new ArrayList<>();
        fields.add("Teacher name: " + safe(teacher.getUser().getFullName()));
        fields.add("Employee ID: " + safe(teacher.getEmployeeId()));
        if (teacher.getDepartment() != null) fields.add("Department: " + safe(teacher.getDepartment()));
        fields.add("Payroll period: " + record.getPayrollYear() + "-" + String.format(Locale.ROOT, "%02d", record.getPayrollMonth()));
        fields.add("Basic salary: " + money(record.getBasicSalary()));
        fields.add("Allowances: " + money(record.getAllowances()));
        fields.add("Bonuses: " + money(record.getBonuses()));
        fields.add("Gross salary: " + money(record.getGrossSalary()));
        fields.add("Deductions: " + money(record.getDeductions()));
        fields.add("Net salary payable: " + money(record.getNetSalary()));
        fields.add("Payment status: " + record.getStatus());
        if (record.getPaymentDate() != null) fields.add("Payment date: " + record.getPaymentDate());
        if (record.getPaymentMethod() != null) fields.add("Payment method: " + safe(record.getPaymentMethod()));
        if (record.getPaymentReference() != null) fields.add("Payment reference: " + safe(record.getPaymentReference()));
        fields.add("Generated at: " + format(LocalDateTime.now()));
        return document("EduERP", "TEACHER SALARY PAYSLIP", fields);
    }

    private byte[] document(String brand, String title, List<String> fields) {
        StringBuilder content = new StringBuilder();
        content.append("q 0.12 0.24 0.40 rg 0 792 595 50 re f Q ");
        text(content, brand, 42, 812, 18, true);
        text(content, "Academic finance and records", 42, 798, 8, false);
        text(content, title, 42, 748, 16, true);
        content.append("0.82 0.85 0.89 RG 1 w 42 738 m 553 738 l S ");
        int y = 710;
        for (String field : fields) {
            String[] pair = field.split(": ", 2);
            text(content, pair[0] + ":", 52, y, 10, true);
            text(content, pair.length == 2 ? pair[1] : "", 220, y, 10, false);
            content.append("0.90 0.91 0.93 RG 0.5 w 52 ").append(y - 8).append(" m 543 ").append(y - 8).append(" l S ");
            y -= 25;
            if (y < 92) break;
        }
        text(content, "This document is system-generated and does not require a signature.", 42, 58, 8, false);
        return pdf(content.toString());
    }

    private void text(StringBuilder content, String value, int x, int y, int size, boolean bold) {
        content.append("BT /").append(bold ? "F2" : "F1").append(" ").append(size).append(" Tf ")
                .append(x).append(" ").append(y).append(" Td (").append(escape(safe(value))).append(") Tj ET ");
    }

    private byte[] pdf(String stream) {
        byte[] streamBytes = stream.getBytes(StandardCharsets.ISO_8859_1);
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                "<< /Length " + streamBytes.length + " >>\nstream\n" + stream + "\nendstream"
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) { offsets[i + 1] = out.size(); write(out, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n"); }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        write(out, "trailer\n<< /Size " + offsets.length + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private String money(BigDecimal value) { return "INR " + (value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP)); }
    private String format(LocalDateTime value) { return value == null ? "Not available" : DATE_TIME.format(value); }
    private String safe(String value) { return value == null || value.isBlank() ? "Not available" : value; }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); }
    private void write(ByteArrayOutputStream out, String value) { out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1)); }
}
