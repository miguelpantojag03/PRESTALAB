package com.prestalab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales para iniciar sesión (RF01)")
public record LoginDTO(
        @Schema(example = "laura@uni.edu.co")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        @Schema(example = "Secreta123")
        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
