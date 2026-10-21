package com.eduerp.repository;
import com.eduerp.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AssessmentRepository extends JpaRepository<Assessment, Long> { List<Assessment> findByCourseId(Long courseId); }
