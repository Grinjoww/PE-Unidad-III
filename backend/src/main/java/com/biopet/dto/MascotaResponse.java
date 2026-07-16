package com.biopet.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public record MascotaResponse(
        Long id,
        Long duenioId,
        String duenioNombre,
        String nombre,
        String especie,
        String raza,
        LocalDate fechaNacimiento,
        boolean activo,
        Instant creadoEn,
        Instant actualizadoEn
) implements Serializable {}
