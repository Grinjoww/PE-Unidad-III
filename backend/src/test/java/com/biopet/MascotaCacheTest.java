package com.biopet;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cache-aside del listado paginado de Mascota usando Redis real (Testcontainers),
 * verificando cache hit/miss reales con un spy del Repository, no un mock.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MascotaCacheTest {

    private static final String PATRON_CLAVES = "mascotas:listado:*";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.cache.type", () -> "redis");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @SpyBean MascotaRepository mascotaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redisTemplate;

    @Value("${security.jwt.cookie.name}") String cookieName;

    @MockBean TokenBlacklistService tokenBlacklistService;

    Usuario duenio;
    Mascota luna;
    Mascota michi;
    Cookie sesionAdmin;

    @BeforeEach
    void setUp() throws Exception {
        var claves = redisTemplate.keys(PATRON_CLAVES);
        if (claves != null && !claves.isEmpty()) {
            redisTemplate.delete(claves);
        }
        mascotaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        usuarioRepository.save(Usuario.builder()
                .nombre("Admin Cache")
                .email("admin.cache@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build());

        duenio = usuarioRepository.save(Usuario.builder()
                .nombre("Dueño Cache")
                .email("duenio.cache@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build());

        luna = mascotaRepository.save(Mascota.builder()
                .duenio(duenio).nombre("Luna").especie("Perro").raza("Labrador")
                .fechaNacimiento(LocalDate.of(2021, 3, 14)).activo(true).build());
        michi = mascotaRepository.save(Mascota.builder()
                .duenio(duenio).nombre("Michi").especie("Gato").raza("Persa")
                .fechaNacimiento(LocalDate.of(2022, 7, 2)).activo(true).build());

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin.cache@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sesionAdmin = loginResult.getResponse().getCookie(cookieName);

        clearInvocations(mascotaRepository);
    }

    @Test
    void primeraConsultaConsultaRepositorioYSegundaConsultaUsaCache() throws Exception {
        String clave = "mascotas:listado:page=0:size=10:sort=id,asc:nombre=:especie=:raza=";
        assertThat(redisTemplate.hasKey(clave)).isFalse();

        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        verify(mascotaRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        assertThat(redisTemplate.hasKey(clave)).isTrue();

        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        verify(mascotaRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void consultaConFiltrosDiferentesGeneraOtraClaveSinReutilizarDatos() throws Exception {
        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/mascotas").param("especie", "gato").cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Michi"));

        verify(mascotaRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));

        assertThat(redisTemplate.hasKey("mascotas:listado:page=0:size=10:sort=id,asc:nombre=:especie=:raza=")).isTrue();
        assertThat(redisTemplate.hasKey("mascotas:listado:page=0:size=10:sort=id,asc:nombre=:especie=gato:raza=")).isTrue();
    }

    @Test
    void consultaConOtraPaginaUOrdenGeneraOtraEntrada() throws Exception {
        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/mascotas").param("sort", "nombre,asc").cookie(sesionAdmin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/mascotas").param("page", "1").param("size", "1").cookie(sesionAdmin)).andExpect(status().isOk());

        verify(mascotaRepository, times(3)).findAll(any(Specification.class), any(Pageable.class));
        assertThat(redisTemplate.keys(PATRON_CLAVES)).hasSize(3);
    }

    @Test
    void crearMascotaInvalidaElCache() throws Exception {
        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin)).andExpect(status().isOk());
        assertThat(redisTemplate.keys(PATRON_CLAVES)).isNotEmpty();

        String body = """
                {"duenioId": %d, "nombre":"Rocky","especie":"Perro","raza":"Beagle","fechaNacimiento":"2020-05-01"}
                """.formatted(duenio.getId());
        mockMvc.perform(post("/api/mascotas").cookie(sesionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(redisTemplate.keys(PATRON_CLAVES)).isEmpty();

        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));

        verify(mascotaRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void actualizarMascotaInvalidaElCache() throws Exception {
        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin)).andExpect(status().isOk());
        assertThat(redisTemplate.keys(PATRON_CLAVES)).isNotEmpty();

        String body = """
                {"duenioId": %d, "nombre":"Luna Actualizada","especie":"Perro","raza":"Labrador","fechaNacimiento":"2021-03-14"}
                """.formatted(duenio.getId());
        mockMvc.perform(put("/api/mascotas/" + luna.getId()).cookie(sesionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(redisTemplate.keys(PATRON_CLAVES)).isEmpty();
    }

    @Test
    void eliminarMascotaInvalidaElCache() throws Exception {
        mockMvc.perform(get("/api/mascotas").cookie(sesionAdmin)).andExpect(status().isOk());
        assertThat(redisTemplate.keys(PATRON_CLAVES)).isNotEmpty();

        mockMvc.perform(delete("/api/mascotas/" + michi.getId()).cookie(sesionAdmin))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.keys(PATRON_CLAVES)).isEmpty();
    }
}
