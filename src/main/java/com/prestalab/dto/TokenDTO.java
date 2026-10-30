package com.prestalab.dto;

import com.prestalab.domain.enums.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token JWT emitido al iniciar sesión; se envía como Authorization: Bearer {token}")
public record TokenDTO(String token,
                       @Schema(example = "Bearer") String tipo,
                       @Schema(example = "3600") long expiraEnSegundos,
                       Rol rol) {
}
