package com.biopet.service;

import com.biopet.dto.MascotaRequest;
import com.biopet.dto.MascotaResponse;
import com.biopet.entity.Mascota;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MascotaService {
    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public MascotaService(MascotaRepository mascotaRepository, UsuarioRepository usuarioRepository) {
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Cacheable(value = "mascotas", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public Page<MascotaResponse> listar(Pageable pageable) {
        return mascotaRepository.findAllByActivoTrue(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MascotaResponse buscar(Long id) {
        return mascotaRepository.findByIdAndActivoTrue(id).map(this::toResponse)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
    }

    @CacheEvict(value = "mascotas", allEntries = true)
    @Transactional
    public MascotaResponse crear(MascotaRequest request) {
        Usuario duenio = usuarioRepository.findById(request.duenioId())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Dueño no encontrado: " + request.duenioId()));
        Mascota mascota = Mascota.builder()
                .duenio(duenio)
                .nombre(request.nombre())
                .especie(request.especie())
                .raza(request.raza())
                .fechaNacimiento(request.fechaNacimiento())
                .activo(true)
                .build();
        return toResponse(mascotaRepository.save(mascota));
    }

    @CacheEvict(value = "mascotas", allEntries = true)
    @Transactional
    public MascotaResponse actualizar(Long id, MascotaRequest request) {
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        Usuario duenio = usuarioRepository.findById(request.duenioId())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Dueño no encontrado: " + request.duenioId()));
        mascota.setDuenio(duenio);
        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        return toResponse(mascotaRepository.save(mascota));
    }

    @CacheEvict(value = "mascotas", allEntries = true)
    @Transactional
    public void eliminar(Long id) {
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        mascota.setActivo(false);
        mascotaRepository.save(mascota);
    }

    private MascotaResponse toResponse(Mascota mascota) {
        return new MascotaResponse(
                mascota.getId(),
                mascota.getDuenio().getId(),
                mascota.getDuenio().getNombre(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getFechaNacimiento(),
                mascota.isActivo(),
                mascota.getCreadoEn(),
                mascota.getActualizadoEn()
        );
    }
}
