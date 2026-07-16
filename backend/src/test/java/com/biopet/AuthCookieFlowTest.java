package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de autenticacion por cookie HttpOnly usando Redis real
 * (Testcontainers), sin simular TokenBlacklistService con Mockito.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCookieFlowTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redisTemplate;

    @Value("${security.jwt.cookie.name}") String cookieName;
    @Value("${security.jwt.secret}") String jwtSecret;

    @BeforeEach
    void setUp() {
        mascotaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        usuarioRepository.save(Usuario.builder()
                .nombre("Jaime Mariscal")
                .email("jaime@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build());
    }

    @Test
    void loginProtegidoLogoutYReutilizacionDeCookieQuedaInvalidada() throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(cookieName))
                .andExpect(cookie().httpOnly(cookieName, true))
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie(cookieName);
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jaime@biopet.com"));

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie))
                .andExpect(status().isNoContent());

        String jti = Jwts.parser()
                .verifyWith((SecretKey) Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(sessionCookie.getValue())
                .getPayload()
                .getId();
        assertThat(redisTemplate.hasKey("jwt:blacklist:" + jti)).isTrue();

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }
}
