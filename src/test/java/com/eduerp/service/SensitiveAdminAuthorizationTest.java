package com.eduerp.service;

import com.eduerp.dto.CertificateDTO;
import com.eduerp.dto.SalaryStructureDTO;
import com.eduerp.entity.Role;
import com.eduerp.entity.User;
import com.eduerp.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensitiveAdminAuthorizationTest {
    @Mock CertificateRepository certificateRepository; @Mock StudentRepository studentRepository; @Mock CourseRepository courseRepository; @Mock UserRepository userRepository;
    @Mock SalaryStructureRepository structureRepository; @Mock PayrollRecordRepository payrollRepository; @Mock TeacherRepository teacherRepository;

    @Test void teacherCannotIssueCertificate() {
        User teacher = User.builder().id(4L).role(Role.TEACHER).build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(teacher));
        CertificateService service = new CertificateService(certificateRepository, studentRepository, courseRepository, userRepository);
        CertificateDTO dto = CertificateDTO.builder().studentId(2L).certificateType("Completion").title("Course").issueDate(LocalDate.now()).build();
        assertThrows(AccessDeniedException.class, () -> service.issue(dto, 4L));
        verify(certificateRepository, never()).save(any());
    }

    @Test void teacherCannotConfigureSalary() {
        User teacher = User.builder().id(4L).role(Role.TEACHER).build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(teacher));
        PayrollService service = new PayrollService(structureRepository, payrollRepository, teacherRepository, userRepository);
        SalaryStructureDTO dto = SalaryStructureDTO.builder().teacherId(8L).grossSalary(BigDecimal.TEN).paymentFrequency("MONTHLY").effectiveDate(LocalDate.now()).build();
        assertThrows(AccessDeniedException.class, () -> service.configure(dto, 4L));
        verify(structureRepository, never()).save(any());
    }
}
