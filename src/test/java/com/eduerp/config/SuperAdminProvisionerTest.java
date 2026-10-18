package com.eduerp.config;

import com.eduerp.entity.Role;
import com.eduerp.entity.User;
import com.eduerp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminProvisionerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SuperAdminProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new SuperAdminProvisioner(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(provisioner, "firstName", "Rohith");
        ReflectionTestUtils.setField(provisioner, "lastName", "Super Admin");
        ReflectionTestUtils.setField(provisioner, "email", "super-admin@example.test");
        ReflectionTestUtils.setField(provisioner, "initialPassword", "local-only-password");
    }

    @Test
    void provisionsExactlyOneAdministratorWithEncodedPassword() {
        when(userRepository.countByRole(Role.ADMINISTRATOR)).thenReturn(0L);
        when(userRepository.findByEmail("super-admin@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("local-only-password")).thenReturn("bcrypt-hash");

        provisioner.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(Role.ADMINISTRATOR, saved.getRole());
        assertEquals("bcrypt-hash", saved.getPassword());
        assertEquals("super-admin@example.test", saved.getEmail());
        assertTrue(saved.getActive());
    }

    @Test
    void doesNotOverwriteExistingAdministrator() {
        when(userRepository.countByRole(Role.ADMINISTRATOR)).thenReturn(1L);

        provisioner.run();

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }
}
