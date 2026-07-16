package com.biopet;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @Value("${security.jwt.cookie.name}") String cookieName;

    @MockBean TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        Usuario usuario = Usuario.builder()
                .nombre("Jaime Mariscal")
                .email("jaime@biopet.com")
                .passwordHash(passwordEncoder.encode("ClaveCorrecta123*"))
                .rol(Rol.ROLE_ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(usuario);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    void loginExitoso() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(cookieName))
                .andExpect(cookie().httpOnly(cookieName, true))
                .andExpect(jsonPath("$.email").value("jaime@biopet.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("accessToken")).isFalse();
        assertThat(json.has("refreshToken")).isFalse();
    }

    @Test
    void loginClaveIncorrecta() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registroEmailDuplicado() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Jaime","email":"jaime@biopet.com","password":"OtraClave123*","rol":"ROLE_ADMIN"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void accesoSinToken() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accesoConCookieValida() throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jaime@biopet.com","password":"ClaveCorrecta123*"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie(cookieName);
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/api/usuarios/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jaime@biopet.com"));
    }
}
