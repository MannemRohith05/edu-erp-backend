package com.eduerp.repository;

import com.eduerp.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByStudentId(Long studentId);

    List<Grade> findByCourseId(Long courseId);

    List<Grade> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<Grade> findByGradeType(String gradeType);

    @Query("SELECT AVG(g.marks / g.totalMarks * 100) FROM Grade g WHERE g.student.id = :studentId")
    Double getAveragePercentageByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT AVG(g.marks / g.totalMarks * 100) FROM Grade g WHERE g.student.id = :studentId AND g.course.id = :courseId")
    Double getAveragePercentageByStudentIdAndCourseId(@Param("studentId") Long studentId,
            @Param("courseId") Long courseId);
}
