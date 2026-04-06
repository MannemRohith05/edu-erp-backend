package com.eduerp.controller;

import com.eduerp.dto.AttendanceDTO;
import com.eduerp.entity.User;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Attendance", description = "Attendance Management APIs")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get attendance by student ID")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceByStudentId(@PathVariable Long studentId) {
        return ResponseEntity.ok(attendanceService.getAttendanceByStudentId(studentId));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Get attendance by course ID")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceByCourseId(@PathVariable Long courseId) {
        return ResponseEntity.ok(attendanceService.getAttendanceByCourseId(courseId));
    }

    @GetMapping("/course/{courseId}/date/{date}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Get attendance by course ID and date")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceByCourseIdAndDate(
            @PathVariable Long courseId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getAttendanceByCourseIdAndDate(courseId, date));
    }

    @GetMapping("/student/{studentId}/range")
    @Operation(summary = "Get attendance by student ID and date range")
    public ResponseEntity<List<AttendanceDTO>> getAttendanceByStudentIdAndDateRange(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(attendanceService.getAttendanceByStudentIdAndDateRange(studentId, startDate, endDate));
    }

    @GetMapping("/student/{studentId}/stats")
    @Operation(summary = "Get attendance statistics for a student")
    public ResponseEntity<AttendanceService.AttendanceStats> getAttendanceStats(@PathVariable Long studentId) {
        return ResponseEntity.ok(attendanceService.getAttendanceStats(studentId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Mark attendance")
    public ResponseEntity<AttendanceDTO> markAttendance(
            @RequestBody AttendanceDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(attendanceService.markAttendance(dto, user.getId()));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Mark attendance for multiple students")
    public ResponseEntity<List<AttendanceDTO>> markBulkAttendance(
            @RequestBody List<AttendanceDTO> dtos,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(attendanceService.markBulkAttendance(dtos, user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Update attendance")
    public ResponseEntity<AttendanceDTO> updateAttendance(
            @PathVariable Long id,
            @RequestBody AttendanceDTO dto) {
        return ResponseEntity.ok(attendanceService.updateAttendance(id, dto));
    }
}
