package com.eduerp.repository;

import com.eduerp.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByEmployeeId(String employeeId);

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByEmployeeId(String employeeId);

    List<Teacher> findByDepartment(String department);

    @Query("SELECT t FROM Teacher t WHERE t.user.active = true")
    List<Teacher> findAllActive();
}
