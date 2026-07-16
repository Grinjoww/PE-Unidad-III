package com.biopet.dto;

import com.biopet.entity.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 80) String password,
        @NotNull Rol rol
) {}
