package org.example;

import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.example.dto.Dto;
import org.example.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuthService authService; // identity schema
    private final RefreshTokenRepository refreshTokenRepository;


    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody Dto.LoginRequest request){
        authService.signup(request);
        return ResponseEntity.ok("Ok");
    }

    @PostMapping("/login")
    public ResponseEntity<Dto.AuthResponse> login(
        @RequestBody Dto.LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        Dto.AuthResponse authResponse = authService.login(request, httpRequest);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Dto.AuthResponse> refresh(
    @RequestBody Dto.RefreshRequest request,
    HttpServletRequest httpRequest
    ) {
        Dto.AuthResponse refreshResponse = authService.refresh(request, httpRequest);

        return ResponseEntity.ok(refreshResponse);
    }

    private boolean verifyMfaCode(String secret, String code) {
        // Implement TOTP verification (Google Authenticator compatible)
//        return new Totp(secret).verify(code);
        return true;
    }
}

// DTOs

