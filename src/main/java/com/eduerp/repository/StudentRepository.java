package com.eduerp.repository;

import com.eduerp.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByStudentId(String studentId);

    Optional<Student> findByUserId(Long userId);

    boolean existsByStudentId(String studentId);

    List<Student> findByDepartment(String department);

    List<Student> findBySemester(Integer semester);

    @Query("SELECT s FROM Student s WHERE s.user.active = true")
    List<Student> findAllActive();

    @Query("SELECT s FROM Student s JOIN s.courses c WHERE c.id = :courseId")
    List<Student> findByCourseId(@Param("courseId") Long courseId);
}
