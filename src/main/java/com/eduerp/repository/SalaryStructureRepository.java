package com.eduerp.repository;

import com.eduerp.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {
    List<SalaryStructure> findByTeacherIdOrderByEffectiveDateDesc(Long teacherId);
}
