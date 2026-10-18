package com.eduerp.controller;

import com.eduerp.dto.CourseDTO;
import com.eduerp.entity.User;
import com.eduerp.entity.Role;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.service.CourseService;
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
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Courses", description = "Course Management APIs")
public class CourseController {

    private final CourseService courseService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    @GetMapping
    @Operation(summary = "Get all courses")
    public ResponseEntity<List<CourseDTO>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get course by ID")
    public ResponseEntity<CourseDTO> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping("/code/{courseCode}")
    @Operation(summary = "Get course by course code")
    public ResponseEntity<CourseDTO> getCourseByCourseCode(@PathVariable String courseCode) {
        return ResponseEntity.ok(courseService.getCourseByCourseCode(courseCode));
    }

    @GetMapping("/teacher/{teacherId}")
    @Operation(summary = "Get courses by teacher ID")
    public ResponseEntity<List<CourseDTO>> getCoursesByTeacherId(@PathVariable Long teacherId) {
        return ResponseEntity.ok(courseService.getCoursesByTeacherId(teacherId));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get courses assigned to the authenticated teacher")
    public ResponseEntity<List<CourseDTO>> getMyCourses(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(courseService.getCoursesForTeacherUser(user.getId()));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get courses by student ID")
    public ResponseEntity<List<CourseDTO>> getCoursesByStudentId(@PathVariable Long studentId, @AuthenticationPrincipal UserDetails details) {
        ensureStudentScope(studentId, details);
        return ResponseEntity.ok(courseService.getCoursesByStudentId(studentId));
    }

    @GetMapping("/department/{department}")
    @Operation(summary = "Get courses by department")
    public ResponseEntity<List<CourseDTO>> getCoursesByDepartment(@PathVariable String department) {
        return ResponseEntity.ok(courseService.getCoursesByDepartment(department));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    @Operation(summary = "Create new course")
    public ResponseEntity<CourseDTO> createCourse(@RequestBody CourseDTO dto) {
        return ResponseEntity.ok(courseService.createCourse(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    @Operation(summary = "Update course")
    public ResponseEntity<CourseDTO> updateCourse(
            @PathVariable Long id,
            @RequestBody CourseDTO dto) {
        return ResponseEntity.ok(courseService.updateCourse(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATOR')")
    @Operation(summary = "Delete course")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/enroll/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT', 'ADMINISTRATOR')")
    @Operation(summary = "Enroll student in course")
    public ResponseEntity<CourseDTO> enrollStudent(
            @PathVariable Long courseId,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserDetails details) {
        ensureStudentScope(studentId, details);
        return ResponseEntity.ok(courseService.enrollStudent(courseId, studentId));
    }

    @DeleteMapping("/{courseId}/unenroll/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT', 'ADMINISTRATOR')")
    @Operation(summary = "Unenroll student from course")
    public ResponseEntity<CourseDTO> unenrollStudent(
            @PathVariable Long courseId,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserDetails details) {
        ensureStudentScope(studentId, details);
        return ResponseEntity.ok(courseService.unenrollStudent(courseId, studentId));
    }

    private void ensureStudentScope(Long studentId, UserDetails details) {
        User user = userRepository.findByEmail(details.getUsername()).orElseThrow();
        if (user.getRole() == Role.STUDENT) {
            Long ownId = studentRepository.findByUserId(user.getId()).orElseThrow().getId();
            if (!ownId.equals(studentId)) throw new org.springframework.security.access.AccessDeniedException("Students may only access their own enrollment");
        }
    }
}
