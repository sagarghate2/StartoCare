package com.startocare.userservice.service;

import com.startocare.userservice.dto.request.RegisterRequest;
import com.startocare.userservice.exception.DuplicateEmailException;
import com.startocare.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void translatesDatabaseUniqueConstraintRaceToDuplicateConflict() {
        when(userRepository.existsByEmail("sagar@example.com")).thenReturn(false);
        when(passwordEncoder.encode("A-Strong-Password-123")).thenReturn("$2a$test-hash");
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique email constraint"));

        assertThrows(DuplicateEmailException.class, () -> userService.register(
                new RegisterRequest("Sagar Ghate", " Sagar@example.com ", "+919876543210",
                        "A-Strong-Password-123", null)
        ));
        verify(userRepository).saveAndFlush(any());
    }
}
