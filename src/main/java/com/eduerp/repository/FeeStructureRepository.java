package com.eduerp.repository;

import com.eduerp.entity.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {
    @Query("SELECT f FROM FeeStructure f WHERE f.course.id = :courseId OR f.course IS NULL")
    List<FeeStructure> findApplicableToCourse(@Param("courseId") Long courseId);
}
