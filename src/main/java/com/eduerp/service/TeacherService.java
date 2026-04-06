package com.eduerp.service;

import com.eduerp.dto.TeacherDTO;
import com.eduerp.entity.Course;
import com.eduerp.entity.Role;
import com.eduerp.entity.Teacher;
import com.eduerp.entity.User;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.TeacherRepository;
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
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<TeacherDTO> getAllTeachers() {
        return teacherRepository.findAllActive().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TeacherDTO getTeacherById(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));
        return mapToDTO(teacher);
    }

    public TeacherDTO getTeacherByEmployeeId(String employeeId) {
        Teacher teacher = teacherRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with employeeId: " + employeeId));
        return mapToDTO(teacher);
    }

    public TeacherDTO getTeacherByUserId(Long userId) {
        Teacher teacher = teacherRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found for user id: " + userId));
        return mapToDTO(teacher);
    }

    public List<TeacherDTO> getTeachersByDepartment(String department) {
        return teacherRepository.findByDepartment(department).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeacherDTO createTeacher(TeacherDTO dto, String password) {
        if (teacherRepository.existsByEmployeeId(dto.getEmployeeId())) {
            throw new IllegalArgumentException("Employee ID already exists");
        }

        User user = User.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(password))
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .role(Role.TEACHER)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        Teacher teacher = Teacher.builder()
                .user(savedUser)
                .employeeId(dto.getEmployeeId())
                .department(dto.getDepartment())
                .qualification(dto.getQualification())
                .specialization(dto.getSpecialization())
                .joiningDate(dto.getJoiningDate())
                .build();

        Teacher savedTeacher = teacherRepository.save(teacher);
        return mapToDTO(savedTeacher);
    }

    @Transactional
    public TeacherDTO updateTeacher(Long id, TeacherDTO dto) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));

        if (dto.getFirstName() != null) {
            teacher.getUser().setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            teacher.getUser().setLastName(dto.getLastName());
        }
        if (dto.getDepartment() != null) {
            teacher.setDepartment(dto.getDepartment());
        }
        if (dto.getQualification() != null) {
            teacher.setQualification(dto.getQualification());
        }
        if (dto.getSpecialization() != null) {
            teacher.setSpecialization(dto.getSpecialization());
        }

        Teacher updatedTeacher = teacherRepository.save(teacher);
        return mapToDTO(updatedTeacher);
    }

    @Transactional
    public void deleteTeacher(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id: " + id));

        teacher.getUser().setActive(false);
        userRepository.save(teacher.getUser());
    }

    private TeacherDTO mapToDTO(Teacher teacher) {
        List<String> courseCodes = teacher.getCourses().stream()
                .map(Course::getCourseCode)
                .collect(Collectors.toList());

        return TeacherDTO.builder()
                .id(teacher.getId())
                .userId(teacher.getUser().getId())
                .email(teacher.getUser().getEmail())
                .firstName(teacher.getUser().getFirstName())
                .lastName(teacher.getUser().getLastName())
                .fullName(teacher.getUser().getFullName())
                .employeeId(teacher.getEmployeeId())
                .department(teacher.getDepartment())
                .qualification(teacher.getQualification())
                .specialization(teacher.getSpecialization())
                .joiningDate(teacher.getJoiningDate())
                .courseCodes(courseCodes)
                .build();
    }
}
