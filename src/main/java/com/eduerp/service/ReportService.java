package com.eduerp.service;

import com.eduerp.entity.Role;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ReportService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final AttendanceRepository attendanceRepository;
    private final GradeRepository gradeRepository;

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalUsers", userRepository.count());
        stats.put("totalStudents", studentRepository.count());
        stats.put("totalTeachers", teacherRepository.count());
        stats.put("totalCourses", courseRepository.count());
        stats.put("totalAdmins", userRepository.findByRole(Role.ADMIN).size());
        stats.put("totalAdministrators", userRepository.findByRole(Role.ADMINISTRATOR).size());

        return stats;
    }

    public Map<String, Object> getEnrollmentStats() {
        Map<String, Object> stats = new HashMap<>();

        var students = studentRepository.findAllActive();

        // Group students by department
        Map<String, Long> byDepartment = new HashMap<>();
        students.forEach(student -> {
            String dept = student.getDepartment() != null ? student.getDepartment() : "Unassigned";
            byDepartment.merge(dept, 1L, Long::sum);
        });

        // Group students by semester
        Map<Integer, Long> bySemester = new HashMap<>();
        students.forEach(student -> {
            Integer sem = student.getSemester() != null ? student.getSemester() : 0;
            bySemester.merge(sem, 1L, Long::sum);
        });

        stats.put("totalEnrolled", students.size());
        stats.put("byDepartment", byDepartment);
        stats.put("bySemester", bySemester);

        return stats;
    }

    public Map<String, Object> getAttendanceReport() {
        Map<String, Object> report = new HashMap<>();

        var allAttendance = attendanceRepository.findAll();

        long present = allAttendance.stream()
                .filter(a -> a.getStatus().name().equals("PRESENT"))
                .count();
        long absent = allAttendance.stream()
                .filter(a -> a.getStatus().name().equals("ABSENT"))
                .count();
        long late = allAttendance.stream()
                .filter(a -> a.getStatus().name().equals("LATE"))
                .count();

        report.put("totalRecords", allAttendance.size());
        report.put("present", present);
        report.put("absent", absent);
        report.put("late", late);
        report.put("attendanceRate", allAttendance.isEmpty() ? 0 : (double) present / allAttendance.size() * 100);

        return report;
    }

    public Map<String, Object> getGradeReport() {
        Map<String, Object> report = new HashMap<>();

        var allGrades = gradeRepository.findAll();

        // Calculate average
        double avgPercentage = allGrades.stream()
                .mapToDouble(g -> (g.getMarks() / g.getTotalMarks()) * 100)
                .average()
                .orElse(0);

        // Group by letter grade
        Map<String, Long> byLetterGrade = new HashMap<>();
        allGrades.forEach(grade -> {
            String letter = grade.getLetterGrade() != null ? grade.getLetterGrade() : "N/A";
            byLetterGrade.merge(letter, 1L, Long::sum);
        });

        report.put("totalGrades", allGrades.size());
        report.put("averagePercentage", avgPercentage);
        report.put("byLetterGrade", byLetterGrade);

        return report;
    }
}
