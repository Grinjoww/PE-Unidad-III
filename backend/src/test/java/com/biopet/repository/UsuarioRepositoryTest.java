package com.biopet.repository;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Solo cubre comportamiento de UsuarioRepository no verificado ya
 * indirectamente por los tests de autenticacion/mascotas: busqueda por
 * email, existencia de email y la restriccion de unicidad.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UsuarioRepositoryTest {

    @Autowired UsuarioRepository usuarioRepository;

    private Usuario nuevoUsuario(String email, boolean activo) {
        return Usuario.builder()
                .nombre("Usuario " + email)
                .email(email)
                .passwordHash("hash-no-usado-en-estas-pruebas")
                .rol(Rol.ROLE_DUENO)
                .activo(activo)
                .build();
    }

    @Test
    void buscarPorEmailExistente() {
        usuarioRepository.save(nuevoUsuario("existe@biopet.com", true));

        assertThat(usuarioRepository.findByEmail("existe@biopet.com")).isPresent();
    }

    @Test
    void buscarPorEmailInexistenteDevuelveVacio() {
        assertThat(usuarioRepository.findByEmail("no-existe@biopet.com")).isEmpty();
    }

    @Test
    void findByEmailAndActivoTrueExcluyeUsuariosInactivos() {
        // @PrePersist fuerza activo=true en la primera insercion (ver
        // Usuario.prePersist), igual que hace el soft-delete real del
        // dominio: se crea activo y luego se desactiva con un update.
        Usuario usuario = usuarioRepository.saveAndFlush(nuevoUsuario("inactivo@biopet.com", true));
        usuario.setActivo(false);
        usuarioRepository.saveAndFlush(usuario);

        assertThat(usuarioRepository.findByEmail("inactivo@biopet.com")).isPresent();
        assertThat(usuarioRepository.findByEmailAndActivoTrue("inactivo@biopet.com")).isEmpty();
    }

    @Test
    void existsByEmailDistingueEmailsRegistrados() {
        usuarioRepository.save(nuevoUsuario("registrado@biopet.com", true));

        assertThat(usuarioRepository.existsByEmail("registrado@biopet.com")).isTrue();
        assertThat(usuarioRepository.existsByEmail("nunca-registrado@biopet.com")).isFalse();
    }

    @Test
    void emailEsUnicoAlNivelDePersistencia() {
        usuarioRepository.saveAndFlush(nuevoUsuario("duplicado@biopet.com", true));

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(nuevoUsuario("duplicado@biopet.com", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
