package com.biopet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Rol rol;

    // Boolean (no boolean primitivo) para poder distinguir "no establecido"
    // (null) de "establecido explicitamente en false" antes de la primera
    // insercion; ver prePersist(). La columna en base de datos sigue siendo
    // NOT NULL: prePersist garantiza que nunca llegue null al INSERT.
    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (activo == null) {
            activo = true;
        }
        if (rol == null) rol = Rol.ROLE_DUENO;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }

    public boolean isActivo() {
        return Boolean.TRUE.equals(activo);
    }
}
