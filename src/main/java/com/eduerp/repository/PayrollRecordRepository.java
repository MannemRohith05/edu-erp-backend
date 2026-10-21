package com.eduerp.repository;

import com.eduerp.entity.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {
    List<PayrollRecord> findByTeacherIdOrderByPayrollYearDescPayrollMonthDesc(Long teacherId);
    Optional<PayrollRecord> findByTeacherIdAndPayrollYearAndPayrollMonth(Long teacherId, Integer year, Integer month);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayrollRecord p where p.id = :id")
    Optional<PayrollRecord> findByIdForUpdate(@Param("id") Long id);
}
