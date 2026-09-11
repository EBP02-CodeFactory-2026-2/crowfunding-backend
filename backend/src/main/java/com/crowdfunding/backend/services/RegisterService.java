package com.crowdfunding.backend.services;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.DuplicatedEmailException;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.register.RegisterRequest;
import com.crowdfunding.backend.register.RegisterResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RegisterService {
    private UserRepository repository;
    private PasswordEncoder passwordEncoder;

    public RegisterService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*\\d)(?=.*[A-Z]).{8,}$");

    public RegisterResponse register(RegisterRequest request) {

        Matcher matcher = PASSWORD_PATTERN.matcher(request.password());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener mínimo 8 caracteres, al menos un número y al menos una letra mayúscula.");
        }

        Optional<User> result = repository.findByEmail(request.email());

        if (result.isPresent()) {
            throw new DuplicatedEmailException("Correo ya existente");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        repository.save(user);

        return new RegisterResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }

}