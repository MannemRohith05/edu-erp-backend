package com.eduerp.service;

import com.eduerp.dto.AuthResponse;
import com.eduerp.dto.EmailRequest;
import com.eduerp.dto.LoginOtpResponse;
import com.eduerp.dto.LoginRequest;
import com.eduerp.dto.RegisterRequest;
import com.eduerp.dto.VerifyOtpRequest;
import com.eduerp.entity.Role;
import com.eduerp.entity.Student;
import com.eduerp.entity.Teacher;
import com.eduerp.entity.User;
import com.eduerp.repository.StudentRepository;
import com.eduerp.repository.TeacherRepository;
import com.eduerp.repository.UserRepository;
import com.eduerp.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;
    private final OtpService otpService;
    private final Map<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();

    public LoginOtpResponse register(RegisterRequest request) {
        Role requestedRole = publicRegistrationRole(request.getRole());
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (requestedRole == Role.TEACHER
                && (!StringUtils.hasText(request.getEmployeeId()) || !StringUtils.hasText(request.getDepartment()))) {
            throw new IllegalArgumentException("Employee ID and department are required for teacher registration");
        }

        pendingRegistrations.put(email, new PendingRegistration(request));
        String otp = otpService.createOtp(email);
        sendOtp(email, otp, "EduERP registration OTP");
        return LoginOtpResponse.builder().message("Verification OTP sent to your email")
                .email(email).expiresInSeconds(otpService.getExpirySeconds()).build();
    }

    @Transactional
    public AuthResponse verifyRegistration(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        otpService.verifyOtp(email, request.getOtp());
        PendingRegistration pending = pendingRegistrations.remove(email);
        if (pending == null) {
            throw new IllegalArgumentException("Registration expired. Please register again.");
        }
        RegisterRequest registration = pending.request();
        Role requestedRole = publicRegistrationRole(registration.getRole());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(registration.getPassword()))
                .firstName(registration.getFirstName())
                .lastName(registration.getLastName())
                .role(requestedRole)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        if (savedUser.getRole() == Role.STUDENT) {
            Student student = Student.builder()
                    .user(savedUser)
                    .studentId("STU2026" + String.format("%04d", savedUser.getId()))
                    .department("CSE")
                    .semester(2)
                    .enrollmentDate(LocalDate.now())
                    .build();
            studentRepository.save(student);
        } else if (savedUser.getRole() == Role.TEACHER) {
            String employeeId = StringUtils.hasText(registration.getEmployeeId())
                    ? registration.getEmployeeId().trim()
                    : "EMP2026" + String.format("%04d", savedUser.getId());
            if (teacherRepository.existsByEmployeeId(employeeId)) {
                throw new IllegalArgumentException("Employee ID already registered");
            }
            Teacher teacher = Teacher.builder()
                    .user(savedUser)
                    .employeeId(employeeId)
                    .department(StringUtils.hasText(registration.getDepartment())
                            ? registration.getDepartment().trim() : "CSE")
                    .joiningDate(LocalDate.now())
                    .build();
            teacherRepository.save(teacher);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    private Role publicRegistrationRole(Role role) {
        Role requestedRole = role == null ? Role.STUDENT : role;
        if (requestedRole != Role.STUDENT && requestedRole != Role.TEACHER) {
            throw new IllegalArgumentException("Public registration is limited to student or teacher accounts");
        }
        return requestedRole;
    }

    public LoginOtpResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()));

        userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String email = request.getEmail().trim().toLowerCase();
        String otp = otpService.createOtp(email);
        sendOtp(email, otp, "EduERP Login OTP");

        return LoginOtpResponse.builder()
                .message("OTP sent to your email")
                .email(email)
                .expiresInSeconds(otpService.getExpirySeconds())
                .build();
    }

    private void sendOtp(String email, String otp, String subject) {
        try {
            emailService.sendEmail(EmailRequest.builder().to(email).subject(subject)
                    .body("Your EduERP verification code is " + otp + ". It expires in "
                            + (otpService.getExpirySeconds() / 60) + " minutes.").build());
        } catch (MailException ex) {
            throw new IllegalArgumentException("Unable to send verification email. Please check SMTP settings.");
        }
    }

    private record PendingRegistration(RegisterRequest request) {}

    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        otpService.verifyOtp(request.getEmail(), request.getOtp());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse refreshToken(String refreshToken) {
        String userEmail = jwtTokenProvider.extractUsername(refreshToken);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

        if (!jwtTokenProvider.isTokenValid(refreshToken, userDetails)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        return buildAuthResponse(user, newAccessToken, refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtExpiration())
                .user(AuthResponse.UserDTO.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole())
                        .build())
                .build();
    }
}
