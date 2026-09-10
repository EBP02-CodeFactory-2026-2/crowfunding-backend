package dto.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Este campo es obligatorio") 
        @Email(message = "El formato del correo no es válido") 
        String email,

        @NotBlank(message = "Este campo es obligatorio") 
        String password

) {}
