package com.biopet.service;

import com.biopet.entity.Rol;
import com.biopet.dto.*;
import com.biopet.entity.Usuario;
import com.biopet.exception.EmailDuplicadoException;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.repository.UsuarioRepository;
import com.biopet.security.JwtService;
import com.biopet.security.TokenBlacklistService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password())
        );
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(authentication.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no existe"));
        return new AuthResponse(
                jwtService.generateAccessToken(usuario),
                jwtService.generateRefreshToken(usuario),
                jwtService.getExpirationMs() / 1000
        );
    }

    public AuthResponse refresh(RefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("El token enviado no es un refresh token");
        }
        String email = jwtService.extractEmail(refreshToken);
        String jti = jwtService.extractJti(refreshToken);
        if (blacklistService.isRevoked(jti)) {
            throw new IllegalArgumentException("Refresh token revocado");
        }
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return new AuthResponse(jwtService.generateAccessToken(usuario), refreshToken, jwtService.getExpirationMs() / 1000);
    }

    public void logout(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT requerido");
        }
        String token = bearerToken.substring(7);
        blacklistService.revoke(jwtService.extractJti(token), jwtService.extractExpiration(token));
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
