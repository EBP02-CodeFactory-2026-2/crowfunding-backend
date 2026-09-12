package com.crowdfunding.backend.services;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.DuplicatedEmailException;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.register.RegisterRequest;
import com.crowdfunding.backend.register.RegisterResponse;

@Service
public class RegisterService {
    private UserRepository repository;
    private PasswordEncoder passwordEncoder;

    public RegisterService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResponse register(RegisterRequest request) {

        Optional<User> result = repository.findByEmail(request.email());

        if (result.isPresent()) {
            throw new DuplicatedEmailException("Este correo ya está registrado");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        repository.save(user);

        return new RegisterResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }

}
