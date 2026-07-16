package com.biopet.service;

import com.biopet.entity.Rol;
import com.biopet.dto.*;
import com.biopet.entity.Usuario;
import com.biopet.exception.EmailDuplicadoException;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.JwtService;
import com.biopet.security.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       TokenBlacklistService blacklistService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    public record LoginResult(String jwt, AuthResponse body) {}

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailDuplicadoException(request.email());
        }
        Usuario usuario = Usuario.builder()
                .nombre(request.nombre())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.ROLE_DUENO)
                .activo(true)
                .build();
        Usuario guardado = usuarioRepository.save(usuario);
        return toResponse(guardado);
    }

    public LoginResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password())
        );
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(authentication.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no existe"));
        String jwt = jwtService.generateAccessToken(usuario);
        AuthResponse body = new AuthResponse(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(),
                "Autenticacion exitosa"
        );
        return new LoginResult(jwt, body);
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            String jti = jwtService.extractJti(token);
            if (jti != null && !jti.isBlank()) {
                blacklistService.revoke(jti, jwtService.extractExpiration(token));
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // token ya invalido o expirado: nada que revocar
        }
    }

    public UsuarioResponse perfil(String email) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return toResponse(usuario);
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.isActivo());
    }
}
