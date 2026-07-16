package com.biopet.repository;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas directas de MascotaRepository contra una base H2 real (perfil test),
 * sin mockear el Repository. Cada metodo corre en su propia transaccion que
 * @DataJpaTest revierte al finalizar, por lo que los tests son independientes
 * entre si y del orden de ejecucion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MascotaRepositoryTest {

    @Autowired MascotaRepository mascotaRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario nuevoDuenio(String email) {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Dueño " + email)
                .email(email)
                .passwordHash("hash-no-usado-en-estas-pruebas")
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build());
    }

    private Mascota nuevaMascota(Usuario duenio, String nombre, String especie, String raza, LocalDate fechaNacimiento) {
        return Mascota.builder()
                .duenio(duenio)
                .nombre(nombre)
                .especie(especie)
                .raza(raza)
                .fechaNacimiento(fechaNacimiento)
                .activo(true)
                .build();
    }

    @Test
    void guardarMascotaValidaGeneraId() {
        Usuario duenio = nuevoDuenio("duenio.guardar@biopet.com");
        Mascota mascota = mascotaRepository.save(nuevaMascota(duenio, "Luna", "Perro", "Labrador", LocalDate.of(2021, 3, 14)));

        assertThat(mascota.getId()).isNotNull();
        assertThat(mascota.getCreadoEn()).isNotNull();
        assertThat(mascota.getActualizadoEn()).isNotNull();
    }

    @Test
    void consultarMascotaPorId() {
        Usuario duenio = nuevoDuenio("duenio.consultar@biopet.com");
        Mascota guardada = mascotaRepository.save(nuevaMascota(duenio, "Rocky", "Perro", "Beagle", LocalDate.of(2020, 5, 1)));

        Optional<Mascota> encontrada = mascotaRepository.findById(guardada.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getNombre()).isEqualTo("Rocky");
    }

    @Test
    void actualizarMascotaPersisteElCambio() {
        Usuario duenio = nuevoDuenio("duenio.actualizar@biopet.com");
        Mascota guardada = mascotaRepository.save(nuevaMascota(duenio, "Nala", "Gato", "Angora", LocalDate.of(2021, 2, 11)));

        guardada.setNombre("Nala Actualizada");
        guardada.setRaza("Persa");
        mascotaRepository.saveAndFlush(guardada);

        Mascota recargada = mascotaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(recargada.getNombre()).isEqualTo("Nala Actualizada");
        assertThat(recargada.getRaza()).isEqualTo("Persa");
    }

    @Test
    void eliminarFisicamenteMascota() {
        Usuario duenio = nuevoDuenio("duenio.eliminar@biopet.com");
        Mascota guardada = mascotaRepository.save(nuevaMascota(duenio, "Toby", "Perro", "Beagle", LocalDate.of(2019, 8, 1)));
        Long id = guardada.getId();

        mascotaRepository.deleteById(id);

        assertThat(mascotaRepository.findById(id)).isEmpty();
    }

    @Test
    void softDeleteExcluyeDeFindByIdAndActivoTrue() {
        Usuario duenio = nuevoDuenio("duenio.softdelete@biopet.com");
        Mascota guardada = mascotaRepository.save(nuevaMascota(duenio, "Coco", "Ave", "Canario", LocalDate.of(2022, 1, 1)));

        guardada.setActivo(false);
        mascotaRepository.saveAndFlush(guardada);

        assertThat(mascotaRepository.findById(guardada.getId())).isPresent();
        assertThat(mascotaRepository.findByIdAndActivoTrue(guardada.getId())).isEmpty();
    }

    @Test
    void mascotaSinActivoExplicitoSeGuardaComoActiva() {
        Usuario duenio = nuevoDuenio("duenio.sinactivo@biopet.com");
        Mascota mascota = Mascota.builder()
                .duenio(duenio)
                .nombre("Sin Activo Explicito")
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2021, 1, 1))
                .build();

        Mascota guardada = mascotaRepository.saveAndFlush(mascota);

        assertThat(guardada.isActivo()).isTrue();
    }

    @Test
    void mascotaConActivoFalseExplicitoConservaFalseAlInsertar() {
        Usuario duenio = nuevoDuenio("duenio.falsoinicio@biopet.com");
        Mascota mascota = Mascota.builder()
                .duenio(duenio)
                .nombre("Inactiva Desde El Inicio")
                .especie("Perro")
                .raza("Mestizo")
                .fechaNacimiento(LocalDate.of(2021, 1, 1))
                .activo(false)
                .build();

        Mascota guardada = mascotaRepository.saveAndFlush(mascota);

        assertThat(guardada.isActivo()).isFalse();
        assertThat(mascotaRepository.findByIdAndActivoTrue(guardada.getId())).isEmpty();
    }

    @Test
    void listadoPaginado() {
        Usuario duenio = nuevoDuenio("duenio.paginado@biopet.com");
        for (int i = 1; i <= 5; i++) {
            mascotaRepository.save(nuevaMascota(duenio, "Mascota" + i, "Perro", "Mestizo", LocalDate.of(2020, 1, 1)));
        }

        var pagina = mascotaRepository.findAll(MascotaSpecifications.activa(), PageRequest.of(0, 2));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
        assertThat(pagina.isFirst()).isTrue();
        assertThat(pagina.isLast()).isFalse();
    }

    @Test
    void ordenamientoPorNombreAscendente() {
        Usuario duenio = nuevoDuenio("duenio.orden@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Zeus", "Perro", "Mestizo", LocalDate.of(2019, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Ana", "Gato", "Mestizo", LocalDate.of(2020, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Milo", "Perro", "Mestizo", LocalDate.of(2021, 1, 1)));

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("nombre")));
        var pagina = mascotaRepository.findAll(MascotaSpecifications.activa(), pageable);

        assertThat(pagina.getContent()).extracting(Mascota::getNombre)
                .containsExactly("Ana", "Milo", "Zeus");
    }

    @Test
    void filtroPorNombreIgnoraMayusculasYMinusculas() {
        Usuario duenio = nuevoDuenio("duenio.filtronombre@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Luna", "Perro", "Labrador", LocalDate.of(2021, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Solecito", "Gato", "Persa", LocalDate.of(2021, 1, 1)));

        Specification<Mascota> spec = MascotaSpecifications.activa().and(MascotaSpecifications.nombreContiene("LUNA"));
        List<Mascota> resultado = mascotaRepository.findAll(spec);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Luna");
    }

    @Test
    void filtroPorEspecie() {
        Usuario duenio = nuevoDuenio("duenio.filtroespecie@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Luna", "Perro", "Labrador", LocalDate.of(2021, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Michi", "Gato", "Persa", LocalDate.of(2021, 1, 1)));

        Specification<Mascota> spec = MascotaSpecifications.activa().and(MascotaSpecifications.especieContiene("gato"));
        List<Mascota> resultado = mascotaRepository.findAll(spec);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Michi");
    }

    @Test
    void filtroPorRaza() {
        Usuario duenio = nuevoDuenio("duenio.filtroraza@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Luna", "Perro", "Labrador", LocalDate.of(2021, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Rex", "Perro", "Pastor Alemán", LocalDate.of(2021, 1, 1)));

        Specification<Mascota> spec = MascotaSpecifications.activa().and(MascotaSpecifications.razaContiene("labrador"));
        List<Mascota> resultado = mascotaRepository.findAll(spec);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Luna");
    }

    @Test
    void combinacionDeNombreEspecieYRaza() {
        Usuario duenio = nuevoDuenio("duenio.combinado@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Luna", "Perro", "Labrador", LocalDate.of(2021, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Luna", "Gato", "Persa", LocalDate.of(2021, 1, 1)));
        mascotaRepository.save(nuevaMascota(duenio, "Rex", "Perro", "Labrador", LocalDate.of(2021, 1, 1)));

        Specification<Mascota> spec = MascotaSpecifications.activa()
                .and(MascotaSpecifications.nombreContiene("luna"))
                .and(MascotaSpecifications.especieContiene("perro"))
                .and(MascotaSpecifications.razaContiene("labrador"));
        List<Mascota> resultado = mascotaRepository.findAll(spec);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Luna");
        assertThat(resultado.get(0).getEspecie()).isEqualTo("Perro");
    }

    @Test
    void consultaSinResultados() {
        Usuario duenio = nuevoDuenio("duenio.sinresultados@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Luna", "Perro", "Labrador", LocalDate.of(2021, 1, 1)));

        Specification<Mascota> spec = MascotaSpecifications.activa().and(MascotaSpecifications.especieContiene("dragon"));
        List<Mascota> resultado = mascotaRepository.findAll(spec);

        assertThat(resultado).isEmpty();
    }

    @Test
    void especificacionActivaExcluyeMascotasInactivas() {
        Usuario duenio = nuevoDuenio("duenio.inactivas@biopet.com");
        mascotaRepository.save(nuevaMascota(duenio, "Activa", "Perro", "Mestizo", LocalDate.of(2021, 1, 1)));
        Mascota inactiva = mascotaRepository.save(nuevaMascota(duenio, "Inactiva", "Perro", "Mestizo", LocalDate.of(2021, 1, 1)));
        inactiva.setActivo(false);
        mascotaRepository.saveAndFlush(inactiva);

        List<Mascota> resultado = mascotaRepository.findAll(MascotaSpecifications.activa());

        assertThat(resultado).extracting(Mascota::getNombre).containsExactly("Activa");
    }

    @Test
    void relacionConDuenioEsObligatoria() {
        Mascota sinDuenio = nuevaMascota(null, "SinDueño", "Perro", "Mestizo", LocalDate.of(2021, 1, 1));

        assertThatThrownBy(() -> mascotaRepository.saveAndFlush(sinDuenio))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void restriccionDeCampoObligatorioEnPersistencia() {
        Usuario duenio = nuevoDuenio("duenio.restriccion@biopet.com");
        Mascota sinNombre = nuevaMascota(duenio, null, "Perro", "Mestizo", LocalDate.of(2021, 1, 1));

        assertThatThrownBy(() -> mascotaRepository.saveAndFlush(sinNombre))
                .isInstanceOf(RuntimeException.class);
    }
}
