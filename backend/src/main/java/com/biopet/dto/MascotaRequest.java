package com.biopet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MascotaRequest(
        @NotNull Long duenioId,
        @NotBlank @Size(max = 50) String nombre,
        @NotBlank @Size(max = 30) String especie,
        @NotBlank @Size(max = 50) String raza,
        @NotNull @PastOrPresent LocalDate fechaNacimiento
) {}
