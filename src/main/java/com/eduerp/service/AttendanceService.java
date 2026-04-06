package com.eduerp.service;

import com.eduerp.dto.AttendanceDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public List<AttendanceDTO> getAttendanceByStudentId(Long studentId) {
        return attendanceRepository.findByStudentId(studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAttendanceByCourseId(Long courseId) {
        return attendanceRepository.findByCourseId(courseId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAttendanceByCourseIdAndDate(Long courseId, LocalDate date) {
        return attendanceRepository.findByCourseIdAndDate(courseId, date).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAttendanceByStudentIdAndDateRange(Long studentId, LocalDate startDate,
            LocalDate endDate) {
        return attendanceRepository.findByStudentIdAndDateRange(studentId, startDate, endDate).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceDTO markAttendance(AttendanceDTO dto, Long markedByUserId) {
        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + dto.getStudentId()));

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + dto.getCourseId()));

        User markedBy = userRepository.findById(markedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + markedByUserId));

        // Check if attendance already exists for this student, course, and date
        var existingAttendance = attendanceRepository.findByStudentIdAndCourseIdAndDate(
                dto.getStudentId(), dto.getCourseId(), dto.getDate());

        Attendance attendance;
        if (existingAttendance.isPresent()) {
            attendance = existingAttendance.get();
            attendance.setStatus(dto.getStatus());
            attendance.setRemarks(dto.getRemarks());
            attendance.setMarkedBy(markedBy);
            attendance.setMarkedAt(LocalDateTime.now());
        } else {
            attendance = Attendance.builder()
                    .student(student)
                    .course(course)
                    .date(dto.getDate())
                    .status(dto.getStatus())
                    .remarks(dto.getRemarks())
                    .markedBy(markedBy)
                    .markedAt(LocalDateTime.now())
                    .build();
        }

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return mapToDTO(savedAttendance);
    }

    @Transactional
    public List<AttendanceDTO> markBulkAttendance(List<AttendanceDTO> dtos, Long markedByUserId) {
        return dtos.stream()
                .map(dto -> markAttendance(dto, markedByUserId))
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceDTO updateAttendance(Long id, AttendanceDTO dto) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with id: " + id));

        if (dto.getStatus() != null) {
            attendance.setStatus(dto.getStatus());
        }
        if (dto.getRemarks() != null) {
            attendance.setRemarks(dto.getRemarks());
        }

        Attendance updatedAttendance = attendanceRepository.save(attendance);
        return mapToDTO(updatedAttendance);
    }

    public AttendanceStats getAttendanceStats(Long studentId) {
        Long present = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.PRESENT);
        Long absent = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.ABSENT);
        Long late = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.LATE);
        Long total = present + absent + late;

        double percentage = total > 0 ? (present + (late * 0.5)) / total * 100 : 0;

        return new AttendanceStats(present, absent, late, total, percentage);
    }

    public record AttendanceStats(Long present, Long absent, Long late, Long total, Double percentage) {
    }

    private AttendanceDTO mapToDTO(Attendance attendance) {
        return AttendanceDTO.builder()
                .id(attendance.getId())
                .studentId(attendance.getStudent().getId())
                .studentName(attendance.getStudent().getUser().getFullName())
                .studentCode(attendance.getStudent().getStudentId())
                .courseId(attendance.getCourse().getId())
                .courseName(attendance.getCourse().getCourseName())
                .date(attendance.getDate())
                .status(attendance.getStatus())
                .remarks(attendance.getRemarks())
                .markedAt(attendance.getMarkedAt())
                .build();
    }
}
