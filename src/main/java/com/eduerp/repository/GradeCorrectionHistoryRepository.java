package com.eduerp.repository;
import com.eduerp.entity.GradeCorrectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GradeCorrectionHistoryRepository extends JpaRepository<GradeCorrectionHistory, Long> { List<GradeCorrectionHistory> findByGradeIdOrderByChangedAtDesc(Long gradeId); }
