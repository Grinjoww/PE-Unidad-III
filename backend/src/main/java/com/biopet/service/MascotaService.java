package com.biopet.service;

import com.biopet.config.CacheConfig;
import com.biopet.dto.MascotaRequest;
import com.biopet.dto.MascotaResponse;
import com.biopet.dto.PaginaResponse;
import com.biopet.entity.Mascota;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.MascotaSpecifications;
import com.biopet.repository.UsuarioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

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

    @Cacheable(
            cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO,
            key = "#root.target.claveListado(#nombre, #especie, #raza, #pageable)"
    )
    @Transactional(readOnly = true)
    public PaginaResponse<MascotaResponse> listar(String nombre, String especie, String raza, Pageable pageable) {
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
        Page<MascotaResponse> pagina = mascotaRepository.findAll(spec, pageable).map(this::toResponse);
        return PaginaResponse.from(pagina);
    }

    /**
     * Clave determinista del listado cacheado: debe diferenciar pagina, tamano,
     * ordenamiento completo y cada filtro para que dos consultas distintas
     * nunca compartan una entrada. Invocada desde el SpEL de @Cacheable
     * (#root.target.claveListado(...)).
     */
    public String claveListado(String nombre, String especie, String raza, Pageable pageable) {
        String sort = pageable.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .collect(Collectors.joining(";"));
        return "page=" + pageable.getPageNumber()
                + ":size=" + pageable.getPageSize()
                + ":sort=" + sort
                + ":nombre=" + normalizarFiltro(nombre)
                + ":especie=" + normalizarFiltro(especie)
                + ":raza=" + normalizarFiltro(raza);
    }

    @Transactional(readOnly = true)
    public MascotaResponse buscar(Long id) {
        return mascotaRepository.findByIdAndActivoTrue(id).map(this::toResponse)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada: " + id));
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO, allEntries = true)
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

    @CacheEvict(cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO, allEntries = true)
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

    @CacheEvict(cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO, allEntries = true)
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

    private String normalizarFiltro(String valor) {
        return StringUtils.hasText(valor) ? valor.trim().toLowerCase() : "";
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
