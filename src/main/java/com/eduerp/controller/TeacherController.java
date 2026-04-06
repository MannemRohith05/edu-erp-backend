package com.eduerp.controller;

import com.eduerp.dto.TeacherDTO;
import com.eduerp.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Teachers", description = "Teacher Management APIs")
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    @Operation(summary = "Get all teachers")
    public ResponseEntity<List<TeacherDTO>> getAllTeachers() {
        return ResponseEntity.ok(teacherService.getAllTeachers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get teacher by ID")
    public ResponseEntity<TeacherDTO> getTeacherById(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getTeacherById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get teacher by employee ID")
    public ResponseEntity<TeacherDTO> getTeacherByEmployeeId(@PathVariable String employeeId) {
        return ResponseEntity.ok(teacherService.getTeacherByEmployeeId(employeeId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get teacher by user ID")
    public ResponseEntity<TeacherDTO> getTeacherByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(teacherService.getTeacherByUserId(userId));
    }

    @GetMapping("/department/{department}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    @Operation(summary = "Get teachers by department")
    public ResponseEntity<List<TeacherDTO>> getTeachersByDepartment(@PathVariable String department) {
        return ResponseEntity.ok(teacherService.getTeachersByDepartment(department));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new teacher")
    public ResponseEntity<TeacherDTO> createTeacher(
            @RequestBody TeacherDTO dto,
            @RequestParam String password) {
        return ResponseEntity.ok(teacherService.createTeacher(dto, password));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update teacher")
    public ResponseEntity<TeacherDTO> updateTeacher(
            @PathVariable Long id,
            @RequestBody TeacherDTO dto) {
        return ResponseEntity.ok(teacherService.updateTeacher(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete teacher (deactivate)")
    public ResponseEntity<Void> deleteTeacher(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ResponseEntity.noContent().build();
    }
}
