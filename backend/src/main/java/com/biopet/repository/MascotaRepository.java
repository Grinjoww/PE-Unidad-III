package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {
    Page<Mascota> findAllByActivoTrue(Pageable pageable);
    Optional<Mascota> findByIdAndActivoTrue(Long id);
}
