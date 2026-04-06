package com.eduerp.controller;

import com.eduerp.dto.GradeDTO;
import com.eduerp.entity.User;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get grades by student ID")
    public ResponseEntity<List<GradeDTO>> getGradesByStudentId(@PathVariable Long studentId) {
        return ResponseEntity.ok(gradeService.getGradesByStudentId(studentId));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'ADMINISTRATOR')")
    @Operation(summary = "Get grades by course ID")
    public ResponseEntity<List<GradeDTO>> getGradesByCourseId(@PathVariable Long courseId) {
        return ResponseEntity.ok(gradeService.getGradesByCourseId(courseId));
    }

    @GetMapping("/student/{studentId}/course/{courseId}")
    @Operation(summary = "Get grades by student and course")
    public ResponseEntity<List<GradeDTO>> getGradesByStudentIdAndCourseId(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(gradeService.getGradesByStudentIdAndCourseId(studentId, courseId));
    }

    @GetMapping("/student/{studentId}/average")
    @Operation(summary = "Get average percentage for a student")
    public ResponseEntity<Double> getAveragePercentage(@PathVariable Long studentId) {
        return ResponseEntity.ok(gradeService.getAveragePercentage(studentId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Add a new grade")
    public ResponseEntity<GradeDTO> addGrade(
            @RequestBody GradeDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(gradeService.addGrade(dto, user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Update a grade")
    public ResponseEntity<GradeDTO> updateGrade(
            @PathVariable Long id,
            @RequestBody GradeDTO dto) {
        return ResponseEntity.ok(gradeService.updateGrade(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Operation(summary = "Delete a grade")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long id) {
        gradeService.deleteGrade(id);
        return ResponseEntity.noContent().build();
    }
}
