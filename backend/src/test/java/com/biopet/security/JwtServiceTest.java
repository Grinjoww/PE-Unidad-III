package com.biopet.security;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba unitaria pura (sin contexto Spring) de JwtService: validacion del
 * secreto en el constructor y el ciclo generar/leer un token.
 */
class JwtServiceTest {

    private static final String SECRETO_VALIDO = "9c8f9a7d6e5b4c3a2d1f0e9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c";

    @Test
    void rechazaSecretoMenorA32Bytes() {
        assertThatThrownBy(() -> new JwtService("secreto-demasiado-corto", 3600000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void getExpirationMsDevuelveElValorConfigurado() {
        JwtService jwtService = new JwtService(SECRETO_VALIDO, 3600000);

        assertThat(jwtService.getExpirationMs()).isEqualTo(3600000);
    }

    @Test
    void generaYLeeUnTokenValidoConJti() {
        JwtService jwtService = new JwtService(SECRETO_VALIDO, 3600000);
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nombre("Jaime")
                .email("jaime@biopet.com")
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();

        String token = jwtService.generateAccessToken(usuario);

        assertThat(jwtService.extractEmail(token)).isEqualTo("jaime@biopet.com");
        assertThat(jwtService.extractJti(token)).isNotBlank();
        assertThat(jwtService.extractExpiration(token)).isAfter(java.time.Instant.now());
    }
}
