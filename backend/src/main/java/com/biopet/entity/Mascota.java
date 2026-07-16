package com.biopet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mascotas")
public class Mascota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "duenio_id", nullable = false)
    private Usuario duenio;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String especie;

    @Column(nullable = false, length = 50)
    private String raza;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

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
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = Instant.now();
    }

    public boolean isActivo() {
        return Boolean.TRUE.equals(activo);
    }
}
