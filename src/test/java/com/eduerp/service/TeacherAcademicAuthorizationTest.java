package com.eduerp.service;

import com.eduerp.dto.AttendanceDTO;
import com.eduerp.dto.GradeDTO;
import com.eduerp.entity.AttendanceStatus;
import com.eduerp.entity.Course;
import com.eduerp.entity.Student;
import com.eduerp.entity.Teacher;
import com.eduerp.entity.User;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.AttendanceRepository;
import com.eduerp.repository.CourseRepository;
import com.eduerp.repository.GradeRepository;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherAcademicAuthorizationTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;

    @Test
    void teacherCannotMarkAttendanceForAnotherTeachersCourse() {
        User actingTeacher = user(10L);
        User assignedTeacherUser = user(20L);
        Course course = course(7L, assignedTeacherUser);
        Student student = Student.builder().id(3L).user(user(30L)).studentId("STU-3").build();

        when(studentRepository.findById(3L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));
        when(userRepository.findById(10L)).thenReturn(Optional.of(actingTeacher));

        AttendanceService service = new AttendanceService(
                attendanceRepository, studentRepository, courseRepository, userRepository);

        AttendanceDTO dto = AttendanceDTO.builder()
                .studentId(3L).courseId(7L).date(LocalDate.now()).status(AttendanceStatus.PRESENT).build();

        assertThrows(AccessDeniedException.class, () -> service.markAttendance(dto, 10L));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void teacherCannotAddGradesForAnotherTeachersCourse() {
        User actingTeacher = user(10L);
        User assignedTeacherUser = user(20L);
        Course course = course(7L, assignedTeacherUser);
        Student student = Student.builder().id(3L).user(user(30L)).studentId("STU-3").build();

        when(studentRepository.findById(3L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));
        when(userRepository.findById(10L)).thenReturn(Optional.of(actingTeacher));

        GradeService service = new GradeService(gradeRepository, studentRepository, courseRepository, userRepository);
        GradeDTO dto = GradeDTO.builder()
                .studentId(3L).courseId(7L).assignmentName("Midterm").marks(80D).totalMarks(100D).build();

        assertThrows(AccessDeniedException.class, () -> service.addGrade(dto, 10L));
        verify(gradeRepository, never()).save(any());
    }

    private static User user(Long id) {
        return User.builder().id(id).email("user" + id + "@example.test")
                .firstName("Test").lastName("User").role(com.eduerp.entity.Role.TEACHER).active(true).build();
    }

    private static Course course(Long id, User assignedTeacherUser) {
        Teacher teacher = Teacher.builder().id(2L).user(assignedTeacherUser).employeeId("EMP-2").build();
        return Course.builder().id(id).courseCode("C-7").courseName("Course").teacher(teacher).build();
    }
}
