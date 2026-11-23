package org.example;

import jakarta.servlet.http.*;
import lombok.*;
import org.example.dto.*;
import org.example.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;
import org.springframework.web.server.*;

import java.time.*;
import java.time.temporal.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    public void signup(Dto.LoginRequest req) {
        if (userRepository.existsByLogin(req.login())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login or email already taken");
        }

        User user = new User();
        user.setLogin(req.login());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);

    }

    public Dto.AuthResponse login(Dto.LoginRequest request, HttpServletRequest httpRequest){
        // Step 1: Authenticate credentials
        Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
        request.login(),
        request.password()
        )
        );

        // Step 2: Load user details
        User user = userRepository.findByLogin(request.login())
        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Step 5: Generate tokens
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String accessToken = jwtUtil.generateAccessToken(
        userDetails,
        user.getClientId()
        );

        String refreshToken = jwtUtil.generateRefreshToken(userDetails);
        RefreshToken entity = new RefreshToken();

        entity.setId(UUID.randomUUID());
        entity.setUserId(user.getId());
        entity.setTokenHash(refreshToken);
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        entity.setIpAddress(httpRequest.getRemoteAddr());
        entity.setUserAgent(httpRequest.getHeader("User-Agent"));

        refreshTokenRepository.save(entity);

        user.lastLoginAt = Instant.now();

        // Step 6: Update last login
        userRepository.save(user);

        return new Dto.AuthResponse(
            accessToken,
            refreshToken,
            Dto.AuthStatus.SUCCESS,
            user.getClientId()
        );
    }

    public Dto.AuthResponse refresh(Dto.RefreshRequest request, HttpServletRequest httpRequest){
        // Validate refresh token
        RefreshToken storedToken = refreshTokenRepository
        .findByTokenHash(request.refreshToken())
        .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
        .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // Load user
        User user = userRepository.findById(storedToken.getUserId())
        .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Rotate refresh token (security best practice)
        refreshTokenRepository.delete(storedToken);

        String newAccessToken = jwtUtil.generateAccessToken(
        new org.springframework.security.core.userdetails.User(
        user.getLogin(),
        "",
        List.of()
        ),
        user.getClientId()
        );

        String newRefreshToken = jwtUtil.generateRefreshToken(
        new org.springframework.security.core.userdetails.User(
        user.getLogin(),
        "",
        List.of()
        )
        );

        refreshTokenRepository.save(new RefreshToken(
        UUID.randomUUID(),
        user.getId(),
        newRefreshToken, UUID.randomUUID(), Instant.now(),
        Instant.now().plus(7, ChronoUnit.DAYS),
        null,
        null,
        httpRequest.getRemoteAddr(),
        httpRequest.getHeader("User-Agent")
        ));

        return new Dto.AuthResponse(
        newAccessToken,
        newRefreshToken,
        Dto.AuthStatus.SUCCESS,
        user.getClientId()
        );
    }
}
