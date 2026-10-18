package com.eduerp.service;

import com.eduerp.dto.ScheduleDTO;
import com.eduerp.entity.Course;
import com.eduerp.entity.Schedule;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.CourseRepository;
import com.eduerp.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final CourseRepository courseRepository;

    public List<ScheduleDTO> getAllSchedules() {
        return scheduleRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ScheduleDTO getScheduleById(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));
        return mapToDTO(schedule);
    }

    public List<ScheduleDTO> getSchedulesByCourseId(Long courseId) {
        return scheduleRepository.findByCourseId(courseId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ScheduleDTO> getSchedulesByTeacherId(Long teacherId) {
        return scheduleRepository.findByTeacherId(teacherId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ScheduleDTO> getSchedulesForTeacherUser(Long userId) {
        return scheduleRepository.findByTeacherUserId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ScheduleDTO> getSchedulesByStudentId(Long studentId) {
        return scheduleRepository.findByStudentId(studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ScheduleDTO> getSchedulesForStudentUser(Long userId) {
        return scheduleRepository.findByStudentUserId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ScheduleDTO> getSchedulesByTeacherIdAndDay(Long teacherId, DayOfWeek dayOfWeek) {
        return scheduleRepository.findByTeacherIdAndDayOfWeek(teacherId, dayOfWeek).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ScheduleDTO createSchedule(ScheduleDTO dto) {
        validateTimeRange(dto);
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + dto.getCourseId()));
        if (course.getTeacher() == null) {
            throw new IllegalArgumentException("Course must have an assigned teacher before scheduling");
        }

        validateConflicts(null, course, dto);

        Schedule schedule = Schedule.builder()
                .course(course)
                .dayOfWeek(dto.getDayOfWeek())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .room(dto.getRoom())
                .building(dto.getBuilding())
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);
        return mapToDTO(savedSchedule);
    }

    @Transactional
    public ScheduleDTO updateSchedule(Long id, ScheduleDTO dto) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));

        Course course = schedule.getCourse();
        if (dto.getCourseId() != null && !dto.getCourseId().equals(course.getId())) {
            course = courseRepository.findById(dto.getCourseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + dto.getCourseId()));
            schedule.setCourse(course);
        }
        ScheduleDTO effective = ScheduleDTO.builder()
                .dayOfWeek(dto.getDayOfWeek() != null ? dto.getDayOfWeek() : schedule.getDayOfWeek())
                .startTime(dto.getStartTime() != null ? dto.getStartTime() : schedule.getStartTime())
                .endTime(dto.getEndTime() != null ? dto.getEndTime() : schedule.getEndTime())
                .room(dto.getRoom() != null ? dto.getRoom() : schedule.getRoom())
                .build();
        validateTimeRange(effective);
        if (course.getTeacher() == null) {
            throw new IllegalArgumentException("Course must have an assigned teacher before scheduling");
        }
        validateConflicts(id, course, effective);

        if (dto.getDayOfWeek() != null) {
            schedule.setDayOfWeek(dto.getDayOfWeek());
        }
        if (dto.getStartTime() != null) {
            schedule.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            schedule.setEndTime(dto.getEndTime());
        }
        if (dto.getRoom() != null) {
            schedule.setRoom(dto.getRoom());
        }
        if (dto.getBuilding() != null) {
            schedule.setBuilding(dto.getBuilding());
        }

        Schedule updatedSchedule = scheduleRepository.save(schedule);
        return mapToDTO(updatedSchedule);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        if (!scheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schedule not found with id: " + id);
        }
        scheduleRepository.deleteById(id);
    }

    private ScheduleDTO mapToDTO(Schedule schedule) {
        return ScheduleDTO.builder()
                .id(schedule.getId())
                .courseId(schedule.getCourse().getId())
                .courseCode(schedule.getCourse().getCourseCode())
                .courseName(schedule.getCourse().getCourseName())
                .teacherName(schedule.getCourse().getTeacher() != null
                        ? schedule.getCourse().getTeacher().getUser().getFullName()
                        : null)
                .dayOfWeek(schedule.getDayOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .room(schedule.getRoom())
                .building(schedule.getBuilding())
                .build();
    }

    private void validateTimeRange(ScheduleDTO dto) {
        if (dto.getDayOfWeek() == null || dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new IllegalArgumentException("Day, start time, and end time are required");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
    }

    private void validateConflicts(Long scheduleId, Course course, ScheduleDTO dto) {
        for (Schedule existing : scheduleRepository.findAll()) {
            if (scheduleId != null && scheduleId.equals(existing.getId())) {
                continue;
            }
            if (existing.getDayOfWeek() != dto.getDayOfWeek()
                    || !overlaps(existing.getStartTime(), existing.getEndTime(), dto.getStartTime(), dto.getEndTime())) {
                continue;
            }

            boolean sameTeacher = course.getTeacher() != null && existing.getCourse().getTeacher() != null
                    && course.getTeacher().getId().equals(existing.getCourse().getTeacher().getId());
            boolean sameRoom = dto.getRoom() != null && !dto.getRoom().isBlank()
                    && dto.getRoom().equalsIgnoreCase(existing.getRoom());
            boolean sharedStudents = course.getEnrolledStudents().stream()
                    .anyMatch(existing.getCourse().getEnrolledStudents()::contains);

            if (sameTeacher || sameRoom || sharedStudents) {
                throw new IllegalArgumentException("Schedule conflicts with an existing teacher, classroom, or student group");
            }
        }
    }

    private boolean overlaps(java.time.LocalTime startA, java.time.LocalTime endA,
                             java.time.LocalTime startB, java.time.LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }
}
