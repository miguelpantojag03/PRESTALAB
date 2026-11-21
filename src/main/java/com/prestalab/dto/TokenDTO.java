package com.prestalab.dto;

import com.prestalab.domain.enums.Rol;

/** Token JWT emitido al iniciar sesión; se envía como Authorization: Bearer {token} */
public record TokenDTO(String token,
                       String tipo,
                       long expiraEnSegundos,
                       Rol rol) {
}
