package com.eduerp.controller;

import com.eduerp.dto.ScheduleDTO;
import com.eduerp.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedules", description = "Schedule Management APIs")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    @Operation(summary = "Get all schedules")
    public ResponseEntity<List<ScheduleDTO>> getAllSchedules() {
        return ResponseEntity.ok(scheduleService.getAllSchedules());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get schedule by ID")
    public ResponseEntity<ScheduleDTO> getScheduleById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getScheduleById(id));
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "Get schedules by course ID")
    public ResponseEntity<List<ScheduleDTO>> getSchedulesByCourseId(@PathVariable Long courseId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByCourseId(courseId));
    }

    @GetMapping("/teacher/{teacherId}")
    @Operation(summary = "Get schedules by teacher ID")
    public ResponseEntity<List<ScheduleDTO>> getSchedulesByTeacherId(@PathVariable Long teacherId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByTeacherId(teacherId));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get schedules by student ID")
    public ResponseEntity<List<ScheduleDTO>> getSchedulesByStudentId(@PathVariable Long studentId) {
        return ResponseEntity.ok(scheduleService.getSchedulesByStudentId(studentId));
    }

    @GetMapping("/teacher/{teacherId}/day/{day}")
    @Operation(summary = "Get schedules by teacher and day")
    public ResponseEntity<List<ScheduleDTO>> getSchedulesByTeacherIdAndDay(
            @PathVariable Long teacherId,
            @PathVariable DayOfWeek day) {
        return ResponseEntity.ok(scheduleService.getSchedulesByTeacherIdAndDay(teacherId, day));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new schedule")
    public ResponseEntity<ScheduleDTO> createSchedule(@RequestBody ScheduleDTO dto) {
        return ResponseEntity.ok(scheduleService.createSchedule(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a schedule")
    public ResponseEntity<ScheduleDTO> updateSchedule(
            @PathVariable Long id,
            @RequestBody ScheduleDTO dto) {
        return ResponseEntity.ok(scheduleService.updateSchedule(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a schedule")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
