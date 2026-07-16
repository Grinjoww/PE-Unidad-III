package com.biopet.config;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedAdmin(UsuarioRepository repo, PasswordEncoder enc) {
        return args -> {
            if (!repo.existsByEmail("admin@biopet.ec")) {
                repo.save(Usuario.builder()
                        .nombre("Administrador BIOPET")
                        .email("admin@biopet.ec")
                        .passwordHash(enc.encode("Admin123*"))
                        .rol(Rol.ROLE_ADMIN)
                        .activo(true)
                        .build());
            }
        };
    }
}