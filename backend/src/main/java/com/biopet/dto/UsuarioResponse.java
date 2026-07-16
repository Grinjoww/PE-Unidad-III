package com.biopet.dto;

import com.biopet.entity.Rol;

public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        Rol rol,
        boolean activo
) {}
