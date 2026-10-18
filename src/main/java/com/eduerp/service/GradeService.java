package com.eduerp.service;

import com.eduerp.dto.GradeDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class GradeService {

    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Value("${app.academic.grade-points:A+=10,A=9,B=8,C=7,D=6,F=0}")
    private String gradePointConfig;

    public List<GradeDTO> getGradesByStudentId(Long studentId) {
        return gradeRepository.findByStudentIdAndPublishedTrue(studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<GradeDTO> getGradesByCourseId(Long courseId, Long requesterId) {
        ensureCourseAccess(courseId, requesterId);
        return gradeRepository.findByCourseId(courseId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<GradeDTO> getGradesByStudentIdAndCourseId(Long studentId, Long courseId) {
        return gradeRepository.findByStudentIdAndCourseId(studentId, courseId).stream().filter(g -> Boolean.TRUE.equals(g.getPublished()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public Double getAveragePercentage(Long studentId) {
        return gradeRepository.getAveragePercentageByStudentId(studentId);
    }

    public AcademicSummary getAcademicSummary(Long studentId) {
        Map<Long, Grade> latest = new LinkedHashMap<>();
        for (Grade grade : gradeRepository.findByStudentIdAndPublishedTrue(studentId)) {
            Grade previous = latest.get(grade.getCourse().getId());
            if (previous == null || (grade.getGradedAt() != null
                    && (previous.getGradedAt() == null || grade.getGradedAt().isAfter(previous.getGradedAt())))) {
                latest.put(grade.getCourse().getId(), grade);
            }
        }
        Map<String, BigDecimal> points = parseGradePoints();
        Map<Integer, BigDecimal> creditsBySemester = new LinkedHashMap<>();
        Map<Integer, BigDecimal> pointsBySemester = new LinkedHashMap<>();
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (Grade grade : latest.values()) {
            Integer creditsValue = grade.getCourse().getCredits();
            if (creditsValue == null || creditsValue <= 0) continue;
            BigDecimal credits = BigDecimal.valueOf(creditsValue);
            BigDecimal weighted = credits.multiply(points.getOrDefault(grade.getLetterGrade(), BigDecimal.ZERO));
            Integer semester = grade.getCourse().getSemester();
            creditsBySemester.merge(semester, credits, BigDecimal::add);
            pointsBySemester.merge(semester, weighted, BigDecimal::add);
            totalCredits = totalCredits.add(credits);
            totalPoints = totalPoints.add(weighted);
        }
        Map<Integer, BigDecimal> sgpa = new LinkedHashMap<>();
        creditsBySemester.forEach((semester, credits) -> sgpa.put(semester,
                pointsBySemester.get(semester).divide(credits, 2, RoundingMode.HALF_UP)));
        BigDecimal cgpa = totalCredits.signum() == 0 ? BigDecimal.ZERO
                : totalPoints.divide(totalCredits, 2, RoundingMode.HALF_UP);
        return new AcademicSummary(cgpa, sgpa, totalCredits);
    }

    @Transactional
    public GradeDTO addGrade(GradeDTO dto, Long gradedByUserId) {
        validateMarks(dto.getMarks(), dto.getTotalMarks());
        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + dto.getStudentId()));

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + dto.getCourseId()));

        User gradedBy = userRepository.findById(gradedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + gradedByUserId));
        ensureTeacherOrAdministrator(gradedBy, course);

        String letterGrade = calculateLetterGrade(dto.getMarks(), dto.getTotalMarks());

        Grade grade = Grade.builder()
                .student(student)
                .course(course)
                .assignmentName(dto.getAssignmentName())
                .marks(dto.getMarks())
                .totalMarks(dto.getTotalMarks())
                .gradeType(dto.getGradeType())
                .letterGrade(letterGrade)
                .feedback(dto.getFeedback())
                .gradedBy(gradedBy)
                .gradedAt(LocalDateTime.now())
                .build();

        Grade savedGrade = gradeRepository.save(grade);
        return mapToDTO(savedGrade);
    }

    @Transactional
    public GradeDTO updateGrade(Long id, GradeDTO dto, Long actingUserId) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found with id: " + id));
        User actingUser = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + actingUserId));
        ensureTeacherOrAdministrator(actingUser, grade.getCourse());

        if (dto.getMarks() != null) {
            grade.setMarks(dto.getMarks());
        }
        if (dto.getTotalMarks() != null) {
            grade.setTotalMarks(dto.getTotalMarks());
        }
        validateMarks(grade.getMarks(), grade.getTotalMarks());
        if (dto.getFeedback() != null) {
            grade.setFeedback(dto.getFeedback());
        }

        // Recalculate letter grade
        grade.setLetterGrade(calculateLetterGrade(grade.getMarks(), grade.getTotalMarks()));

        Grade updatedGrade = gradeRepository.save(grade);
        return mapToDTO(updatedGrade);
    }

    @Transactional
    public void deleteGrade(Long id, Long actingUserId) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found with id: " + id));
        User actingUser = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + actingUserId));
        ensureTeacherOrAdministrator(actingUser, grade.getCourse());
        gradeRepository.deleteById(id);
    }

    private String calculateLetterGrade(Double marks, Double totalMarks) {
        if (totalMarks == null || totalMarks == 0)
            return "N/A";
        double percentage = (marks / totalMarks) * 100;

        if (percentage >= 90)
            return "A+";
        if (percentage >= 80)
            return "A";
        if (percentage >= 70)
            return "B";
        if (percentage >= 60)
            return "C";
        if (percentage >= 50)
            return "D";
        return "F";
    }

    private GradeDTO mapToDTO(Grade grade) {
        return GradeDTO.builder()
                .id(grade.getId())
                .studentId(grade.getStudent().getId())
                .studentName(grade.getStudent().getUser().getFullName())
                .studentCode(grade.getStudent().getStudentId())
                .courseId(grade.getCourse().getId())
                .courseName(grade.getCourse().getCourseName())
                .assignmentName(grade.getAssignmentName())
                .marks(grade.getMarks())
                .totalMarks(grade.getTotalMarks())
                .percentage(grade.getPercentage())
                .gradeType(grade.getGradeType())
                .letterGrade(grade.getLetterGrade())
                .published(grade.getPublished())
                .feedback(grade.getFeedback())
                .gradedAt(grade.getGradedAt())
                .build();
    }

    private void validateMarks(Double marks, Double totalMarks) {
        if (marks == null || totalMarks == null || totalMarks <= 0 || marks < 0 || marks > totalMarks) {
            throw new IllegalArgumentException("Marks must be between zero and the positive maximum marks");
        }
    }

    @Transactional
    public GradeDTO publishGrade(Long id) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found with id: " + id));
        grade.setPublished(true);
        return mapToDTO(gradeRepository.save(grade));
    }

    private Map<String, BigDecimal> parseGradePoints() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String item : gradePointConfig.split(",")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2) result.put(pair[0].trim(), new BigDecimal(pair[1].trim()));
        }
        return result;
    }

    public record AcademicSummary(BigDecimal cgpa, Map<Integer, BigDecimal> sgpaBySemester,
                                  BigDecimal eligibleCredits) {}

    private void ensureCourseAccess(Long courseId, Long requesterId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + courseId));
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));
        ensureTeacherOrAdministrator(requester, course);
    }

    private void ensureTeacherOrAdministrator(User user, Course course) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.ADMINISTRATOR) {
            return;
        }
        if (user.getRole() == Role.TEACHER
                && course.getTeacher() != null
                && course.getTeacher().getUser() != null
                && user.getId().equals(course.getTeacher().getUser().getId())) {
            return;
        }
        throw new AccessDeniedException("You are not authorized to access this course");
    }
}
