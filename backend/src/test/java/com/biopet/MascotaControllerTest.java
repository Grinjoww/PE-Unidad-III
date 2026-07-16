package com.biopet;

import com.biopet.entity.Mascota;
import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.MascotaRepository;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MascotaControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MascotaRepository mascotaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @Value("${security.jwt.cookie.name}") String cookieName;

    @MockBean TokenBlacklistService tokenBlacklistService;

    Usuario duenio;
    Cookie sesionAdmin;
    Cookie sesionDuenio;

    @BeforeEach
    void setUp() throws Exception {
        mascotaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);

        usuarioRepository.save(Usuario.builder()
                .nombre("Admin Test")
                .email("admin.test@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build());

        duenio = usuarioRepository.save(Usuario.builder()
                .nombre("Dueño Test")
                .email("duenio.test@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build());

        var loginAdmin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin.test@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sesionAdmin = loginAdmin.getResponse().getCookie(cookieName);

        var loginDuenio = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"duenio.test@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        sesionDuenio = loginDuenio.getResponse().getCookie(cookieName);
    }

    private Mascota guardarMascota(String nombre, String especie, String raza, LocalDate fechaNacimiento) {
        return mascotaRepository.save(Mascota.builder()
                .duenio(duenio)
                .nombre(nombre)
                .especie(especie)
                .raza(raza)
                .fechaNacimiento(fechaNacimiento)
                .activo(true)
                .build());
    }

    @Test
    void crearMascota() throws Exception {
        String body = """
                {"duenioId": %d, "nombre":"Luna","especie":"Perro","raza":"Labrador","fechaNacimiento":"2021-03-14"}
                """.formatted(duenio.getId());

        mockMvc.perform(post("/api/mascotas").cookie(sesionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Luna"))
                .andExpect(jsonPath("$.duenioId").value(duenio.getId()))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void crearMascotaConRolDuenioDevuelve403() throws Exception {
        String body = """
                {"duenioId": %d, "nombre":"Luna","especie":"Perro","raza":"Labrador","fechaNacimiento":"2021-03-14"}
                """.formatted(duenio.getId());

        mockMvc.perform(post("/api/mascotas").cookie(sesionDuenio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearMascotaConDatosInvalidosDevuelve400() throws Exception {
        String body = """
                {"duenioId": %d, "nombre":"","especie":"Perro","raza":"Labrador","fechaNacimiento":"2021-03-14"}
                """.formatted(duenio.getId());

        mockMvc.perform(post("/api/mascotas").cookie(sesionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscarPorIdExistente() throws Exception {
        Mascota mascota = guardarMascota("Rocky", "Perro", "Beagle", LocalDate.of(2020, 5, 1));

        mockMvc.perform(get("/api/mascotas/" + mascota.getId()).cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rocky"));
    }

    @Test
    void buscarPorIdInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/mascotas/999999").cookie(sesionAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarMascota() throws Exception {
        Mascota mascota = guardarMascota("Nala", "Gato", "Angora", LocalDate.of(2021, 2, 11));
        String body = """
                {"duenioId": %d, "nombre":"Nala Actualizada","especie":"Gato","raza":"Persa","fechaNacimiento":"2021-02-11"}
                """.formatted(duenio.getId());

        mockMvc.perform(put("/api/mascotas/" + mascota.getId()).cookie(sesionAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nala Actualizada"))
                .andExpect(jsonPath("$.raza").value("Persa"));
    }

    @Test
    void eliminarMascotaEsSoftDeleteYDejaDeAparecer() throws Exception {
        Mascota mascota = guardarMascota("Toby", "Perro", "Beagle", LocalDate.of(2019, 8, 1));

        mockMvc.perform(delete("/api/mascotas/" + mascota.getId()).cookie(sesionAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/mascotas/" + mascota.getId()).cookie(sesionAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarConPaginacionYOrdenamiento() throws Exception {
        guardarMascota("Zeus", "Perro", "Pastor Alemán", LocalDate.of(2019, 1, 1));
        guardarMascota("Ana", "Gato", "Siamés", LocalDate.of(2020, 1, 1));
        guardarMascota("Milo", "Perro", "Poodle", LocalDate.of(2021, 1, 1));

        mockMvc.perform(get("/api/mascotas")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "nombre,asc")
                        .cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].nombre").value("Ana"))
                .andExpect(jsonPath("$.content[1].nombre").value("Milo"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void listarConOrdenamientoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/mascotas")
                        .param("sort", "campoInventado,asc")
                        .cookie(sesionAdmin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarConFiltrosCombinados() throws Exception {
        guardarMascota("Luna", "Perro", "Labrador", LocalDate.of(2020, 1, 1));
        guardarMascota("Michi", "Gato", "Persa", LocalDate.of(2021, 1, 1));
        guardarMascota("Coco", "Gato", "Siamés", LocalDate.of(2022, 1, 1));

        mockMvc.perform(get("/api/mascotas")
                        .param("especie", "gato")
                        .param("raza", "persa")
                        .cookie(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Michi"));
    }
}
