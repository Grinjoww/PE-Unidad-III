package com.biopet.dto;

import com.biopet.entity.Rol;

public record AuthResponse(
        Long id,
        String nombre,
        String email,
        Rol rol,
        String mensaje
) {}
