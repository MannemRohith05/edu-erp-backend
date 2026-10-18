package com.eduerp.service;

import com.eduerp.dto.RegisterRequest;
import com.eduerp.entity.Role;
import com.eduerp.entity.User;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.TeacherRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRegistrationTest {

    @Mock private UserRepository userRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @Mock private EmailService emailService;
    @Mock private OtpService otpService;
    @Mock private UserDetails userDetails;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, studentRepository, teacherRepository,
                passwordEncoder, jwtTokenProvider, authenticationManager, userDetailsService,
                emailService, otpService);
        lenient().when(otpService.createOtp(any())).thenReturn("123456");
        lenient().when(otpService.getExpirySeconds()).thenReturn(300);
    }

    @Test
    void defaultsPublicRegistrationToStudent() {
        when(userRepository.existsByEmail("student@example.com")).thenReturn(false);

        authService.register(RegisterRequest.builder()
                .email("student@example.com")
                .password("password")
                .firstName("Student")
                .lastName("User")
                .build());

        verify(otpService).createOtp("student@example.com");
    }

    @Test
    void rejectsPrivilegedPublicRegistrationRole() {
        RegisterRequest request = RegisterRequest.builder()
                .email("admin@example.com")
                .password("password")
                .firstName("Admin")
                .lastName("Attempt")
                .role(Role.ADMINISTRATOR)
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(otpService, never()).createOtp(any());
    }

    @Test
    void requiresTeacherFields() {
        RegisterRequest request = RegisterRequest.builder()
                .email("teacher@example.com")
                .password("password")
                .firstName("Teacher")
                .lastName("User")
                .role(Role.TEACHER)
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(otpService, never()).createOtp(any());
    }

    @Test
    void rejectsDuplicateEmailBeforeSendingOtp() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("password")
                .firstName("Existing")
                .lastName("User")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(otpService, never()).createOtp(any());
    }

    @Test
    void verifiesTeacherRegistrationAndCreatesTeacherRole() {
        RegisterRequest request = RegisterRequest.builder()
                .email("teacher@example.com")
                .password("password")
                .firstName("Teacher")
                .lastName("User")
                .role(Role.TEACHER)
                .employeeId("EMP-001")
                .department("CSE")
                .build();
        User savedUser = User.builder().id(8L).email("teacher@example.com")
                .password("encoded").firstName("Teacher").lastName("User")
                .role(Role.TEACHER).active(true).build();

        when(userRepository.existsByEmail("teacher@example.com")).thenReturn(false);
        when(teacherRepository.existsByEmployeeId("EMP-001")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userDetailsService.loadUserByUsername("teacher@example.com")).thenReturn(userDetails);
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(userDetails)).thenReturn("refresh");
        when(jwtTokenProvider.getJwtExpiration()).thenReturn(900000L);
        doNothing().when(otpService).verifyOtp("teacher@example.com", "123456");

        authService.register(request);
        var response = authService.verifyRegistration(
                new com.eduerp.dto.VerifyOtpRequest("teacher@example.com", "123456"));

        assertEquals(Role.TEACHER, response.getUser().getRole());
        verify(teacherRepository).save(any());
    }
}
