package com.biopet.service;

import com.biopet.dto.MascotaRequest;
import com.biopet.dto.MascotaResponse;
import com.biopet.entity.Mascota;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.MascotaSpecifications;
import com.biopet.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
public class MascotaService {
    private static final Set<String> ORDENAMIENTO_PERMITIDO = Set.of(
            "id", "nombre", "especie", "raza", "fechaNacimiento", "creadoEn", "actualizadoEn"
    );

    private final MascotaRepository mascotaRepository;
    private final UsuarioRepository usuarioRepository;

    public MascotaService(MascotaRepository mascotaRepository, UsuarioRepository usuarioRepository) {
        this.mascotaRepository = mascotaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Page<MascotaResponse> listar(String nombre, String especie, String raza, Pageable pageable) {
        validarOrdenamiento(pageable);
        Specification<Mascota> spec = MascotaSpecifications.activa();
        if (StringUtils.hasText(nombre)) {
            spec = spec.and(MascotaSpecifications.nombreContiene(nombre));
        }
        if (StringUtils.hasText(especie)) {
            spec = spec.and(MascotaSpecifications.especieContiene(especie));
        }
        if (StringUtils.hasText(raza)) {
            spec = spec.and(MascotaSpecifications.razaContiene(raza));
        }
        return mascotaRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MascotaResponse buscar(Long id) {
        return mascotaRepository.findByIdAndActivoTrue(id).map(this::toResponse)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
    }

    @Transactional
    public MascotaResponse crear(MascotaRequest request) {
        Usuario duenio = buscarDuenio(request.duenioId());
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

    @Transactional
    public MascotaResponse actualizar(Long id, MascotaRequest request) {
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        Usuario duenio = buscarDuenio(request.duenioId());
        mascota.setDuenio(duenio);
        mascota.setNombre(request.nombre());
        mascota.setEspecie(request.especie());
        mascota.setRaza(request.raza());
        mascota.setFechaNacimiento(request.fechaNacimiento());
        return toResponse(mascotaRepository.save(mascota));
    }

    @Transactional
    public void eliminar(Long id) {
        Mascota mascota = mascotaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
        mascota.setActivo(false);
        mascotaRepository.save(mascota);
    }

    private Usuario buscarDuenio(Long duenioId) {
        return usuarioRepository.findById(duenioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Dueño no encontrado: " + duenioId));
    }

    private void validarOrdenamiento(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!ORDENAMIENTO_PERMITIDO.contains(order.getProperty())) {
                throw new IllegalArgumentException("Campo de ordenamiento no permitido: " + order.getProperty());
            }
        }
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
