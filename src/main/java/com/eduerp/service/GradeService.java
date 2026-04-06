package com.eduerp.service;

import com.eduerp.dto.GradeDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    public List<GradeDTO> getGradesByStudentId(Long studentId) {
        return gradeRepository.findByStudentId(studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<GradeDTO> getGradesByCourseId(Long courseId) {
        return gradeRepository.findByCourseId(courseId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<GradeDTO> getGradesByStudentIdAndCourseId(Long studentId, Long courseId) {
        return gradeRepository.findByStudentIdAndCourseId(studentId, courseId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public Double getAveragePercentage(Long studentId) {
        return gradeRepository.getAveragePercentageByStudentId(studentId);
    }

    @Transactional
    public GradeDTO addGrade(GradeDTO dto, Long gradedByUserId) {
        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + dto.getStudentId()));

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + dto.getCourseId()));

        User gradedBy = userRepository.findById(gradedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + gradedByUserId));

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
    public GradeDTO updateGrade(Long id, GradeDTO dto) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found with id: " + id));

        if (dto.getMarks() != null) {
            grade.setMarks(dto.getMarks());
        }
        if (dto.getTotalMarks() != null) {
            grade.setTotalMarks(dto.getTotalMarks());
        }
        if (dto.getFeedback() != null) {
            grade.setFeedback(dto.getFeedback());
        }

        // Recalculate letter grade
        grade.setLetterGrade(calculateLetterGrade(grade.getMarks(), grade.getTotalMarks()));

        Grade updatedGrade = gradeRepository.save(grade);
        return mapToDTO(updatedGrade);
    }

    @Transactional
    public void deleteGrade(Long id) {
        if (!gradeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Grade not found with id: " + id);
        }
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
                .feedback(grade.getFeedback())
                .gradedAt(grade.getGradedAt())
                .build();
    }
}
