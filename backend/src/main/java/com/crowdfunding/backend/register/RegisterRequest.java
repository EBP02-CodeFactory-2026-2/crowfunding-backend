package com.crowdfunding.backend.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank( message = "El nombre completo es requerido") 
        String fullName,
        @NotBlank( message = "El email es requerido")
        @Email  
        String email,
        @NotBlank( message = "La contraseña es requerida")
        @Size (min = 8, message = "La contraseña debe tener al menos 8 caracteres") 
        String password
) {

}
