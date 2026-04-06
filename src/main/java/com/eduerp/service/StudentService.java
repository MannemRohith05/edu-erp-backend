package com.eduerp.service;

import com.eduerp.dto.StudentDTO;
import com.eduerp.entity.Course;
import com.eduerp.entity.Role;
import com.eduerp.entity.Student;
import com.eduerp.entity.User;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<StudentDTO> getAllStudents() {
        return studentRepository.findAllActive().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public StudentDTO getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        return mapToDTO(student);
    }

    public StudentDTO getStudentByStudentId(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with studentId: " + studentId));
        return mapToDTO(student);
    }

    public StudentDTO getStudentByUserId(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found for user id: " + userId));
        return mapToDTO(student);
    }

    public List<StudentDTO> getStudentsByDepartment(String department) {
        return studentRepository.findByDepartment(department).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<StudentDTO> getStudentsByCourseId(Long courseId) {
        return studentRepository.findByCourseId(courseId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public StudentDTO createStudent(StudentDTO dto, String password) {
        if (studentRepository.existsByStudentId(dto.getStudentId())) {
            throw new IllegalArgumentException("Student ID already exists");
        }

        User user = User.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(password))
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .role(Role.STUDENT)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        Student student = Student.builder()
                .user(savedUser)
                .studentId(dto.getStudentId())
                .dateOfBirth(dto.getDateOfBirth())
                .enrollmentDate(dto.getEnrollmentDate())
                .department(dto.getDepartment())
                .semester(dto.getSemester())
                .parentContact(dto.getParentContact())
                .address(dto.getAddress())
                .build();

        Student savedStudent = studentRepository.save(student);
        return mapToDTO(savedStudent);
    }

    @Transactional
    public StudentDTO updateStudent(Long id, StudentDTO dto) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));

        if (dto.getFirstName() != null) {
            student.getUser().setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            student.getUser().setLastName(dto.getLastName());
        }
        if (dto.getDateOfBirth() != null) {
            student.setDateOfBirth(dto.getDateOfBirth());
        }
        if (dto.getDepartment() != null) {
            student.setDepartment(dto.getDepartment());
        }
        if (dto.getSemester() != null) {
            student.setSemester(dto.getSemester());
        }
        if (dto.getParentContact() != null) {
            student.setParentContact(dto.getParentContact());
        }
        if (dto.getAddress() != null) {
            student.setAddress(dto.getAddress());
        }

        Student updatedStudent = studentRepository.save(student);
        return mapToDTO(updatedStudent);
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));

        student.getUser().setActive(false);
        userRepository.save(student.getUser());
    }

    private StudentDTO mapToDTO(Student student) {
        List<String> courseCodes = student.getCourses().stream()
                .map(Course::getCourseCode)
                .collect(Collectors.toList());

        return StudentDTO.builder()
                .id(student.getId())
                .userId(student.getUser().getId())
                .email(student.getUser().getEmail())
                .firstName(student.getUser().getFirstName())
                .lastName(student.getUser().getLastName())
                .fullName(student.getUser().getFullName())
                .studentId(student.getStudentId())
                .dateOfBirth(student.getDateOfBirth())
                .enrollmentDate(student.getEnrollmentDate())
                .department(student.getDepartment())
                .semester(student.getSemester())
                .parentContact(student.getParentContact())
                .address(student.getAddress())
                .courseCodes(courseCodes)
                .build();
    }
}
