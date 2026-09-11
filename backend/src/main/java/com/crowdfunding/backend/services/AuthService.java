package com.crowdfunding.backend.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.AccountLockedException;
import com.crowdfunding.backend.exception.InvalidCredentialsException;
import com.crowdfunding.backend.login.LoginRequest;
import com.crowdfunding.backend.login.LoginResponse;
import com.crowdfunding.backend.login.LoginResponse.UserSummaryLoginResponse;
import com.crowdfunding.backend.persistence.UserRepository;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository repository;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager, UserRepository repository, JwtService jwtService,
            LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email();

        // CA4: si el correo ya está bloqueado, ni siquiera se intenta autenticar
        if (loginAttemptService.isLocked(email)) {
            throw new AccountLockedException(loginAttemptService.getRemainingSeconds(email));
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            // Cubre tanto "email no existe" (UsernameNotFoundException, oculta por
            // Spring como BadCredentialsException por defecto) como "password
            // incorrecta" (BadCredentialsException). CA2 exige no distinguir
            // cuál de los dos falló, así que ambas terminan igual aquí.
            loginAttemptService.registerFailedAttempt(email);
            throw new InvalidCredentialsException("Contraseña o correo inválidos");
        }

        // Login exitoso: se limpia cualquier historial de intentos fallidos previos
        loginAttemptService.resetAttempts(email);

        // El usuario existe con certeza en este punto (ya se autenticó contra él)
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Contraseña o correo inválidos"));

        String token = jwtService.generateToken(user.getEmail());

        LoginResponse.UserSummaryLoginResponse userSummary = new UserSummaryLoginResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail());

        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), userSummary);

    }
}
