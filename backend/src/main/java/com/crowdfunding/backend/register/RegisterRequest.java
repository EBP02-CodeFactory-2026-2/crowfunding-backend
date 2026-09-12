package com.crowdfunding.backend.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank( message = "El nombre completo es requerido") 
        String fullName,
        @NotBlank( message = "El email es requerido")
        @Email(message = "Formato de correo inválido") 
        String email,
        @NotBlank( message = "La contraseña es requerida")
        @Pattern(
            regexp = "^(?=.*\\d)(?=.*[A-Z]).{8,}$", 
            message = "La contraseña debe tener mínimo 8 caracteres, al menos un número y al menos una letra mayúscula."
        )
        String password
) {

}
