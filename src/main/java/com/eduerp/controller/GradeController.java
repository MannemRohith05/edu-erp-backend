package com.eduerp.controller;

import com.eduerp.dto.GradeDTO;
import com.eduerp.entity.User;
import com.eduerp.repository.UserRepository;
import com.eduerp.repository.StudentRepository;
import com.eduerp.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Grades", description = "Grade Management APIs")
public class GradeController {

    private final GradeService gradeService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<GradeDTO>> getMyGrades(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Long studentId = studentRepository.findByUserId(user.getId()).orElseThrow().getId();
        return ResponseEntity.ok(gradeService.getGradesByStudentId(studentId));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get grades by student ID")
    public ResponseEntity<List<GradeDTO>> getGradesByStudentId(@PathVariable Long studentId, @AuthenticationPrincipal UserDetails details) {
        ensureStudentScope(studentId, details);
        return ResponseEntity.ok(gradeService.getGradesByStudentId(studentId));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Get grades by course ID")
    public ResponseEntity<List<GradeDTO>> getGradesByCourseId(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(gradeService.getGradesByCourseId(courseId, user.getId()));
    }

    @GetMapping("/student/{studentId}/course/{courseId}")
    @Operation(summary = "Get grades by student and course")
    public ResponseEntity<List<GradeDTO>> getGradesByStudentIdAndCourseId(
            @PathVariable Long studentId,
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserDetails details) {
        ensureStudentScope(studentId, details);
        return ResponseEntity.ok(gradeService.getGradesByStudentIdAndCourseId(studentId, courseId));
    }

    @GetMapping("/student/{studentId}/average")
    @Operation(summary = "Get average percentage for a student")
    public ResponseEntity<Double> getAveragePercentage(@PathVariable Long studentId, @AuthenticationPrincipal UserDetails details) {
        ensureStudentScope(studentId, details);
        return ResponseEntity.ok(gradeService.getAveragePercentage(studentId));
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GradeService.AcademicSummary> getMyAcademicSummary(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Long studentId = studentRepository.findByUserId(user.getId()).orElseThrow().getId();
        return ResponseEntity.ok(gradeService.getAcademicSummary(studentId));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    public ResponseEntity<GradeDTO> publishGrade(@PathVariable Long id) {
        return ResponseEntity.ok(gradeService.publishGrade(id));
    }

    private void ensureStudentScope(Long studentId, UserDetails details) {
        User user = userRepository.findByEmail(details.getUsername()).orElseThrow();
        if (user.getRole() == com.eduerp.entity.Role.STUDENT) {
            Long ownId = studentRepository.findByUserId(user.getId()).orElseThrow().getId();
            if (!ownId.equals(studentId)) throw new AccessDeniedException("Students may only access their own grades");
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Add a new grade")
    public ResponseEntity<GradeDTO> addGrade(
            @RequestBody GradeDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(gradeService.addGrade(dto, user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Update a grade")
    public ResponseEntity<GradeDTO> updateGrade(
            @PathVariable Long id,
            @RequestBody GradeDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(gradeService.updateGrade(id, dto, user.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Delete a grade")
    public ResponseEntity<Void> deleteGrade(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        gradeService.deleteGrade(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
