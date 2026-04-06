package com.eduerp.controller;

import com.eduerp.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
@Tag(name = "Reports", description = "Report Generation APIs")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(reportService.getDashboardStats());
    }

    @GetMapping("/enrollment")
    @Operation(summary = "Get enrollment statistics")
    public ResponseEntity<Map<String, Object>> getEnrollmentStats() {
        return ResponseEntity.ok(reportService.getEnrollmentStats());
    }

    @GetMapping("/attendance")
    @Operation(summary = "Get attendance report")
    public ResponseEntity<Map<String, Object>> getAttendanceReport() {
        return ResponseEntity.ok(reportService.getAttendanceReport());
    }

    @GetMapping("/grades")
    @Operation(summary = "Get grade report")
    public ResponseEntity<Map<String, Object>> getGradeReport() {
        return ResponseEntity.ok(reportService.getGradeReport());
    }
}
