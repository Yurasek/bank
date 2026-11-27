package org.example;

import jakarta.servlet.http.*;
import lombok.*;
import org.example.client.*;
import org.example.dto.*;
import org.example.enums.*;
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
    private final ClientService clientService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    public void signup(Dto.LoginRequest req) {
        if (userRepository.existsBySecondNameAndFirstName(req.firstName(), req.secondName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login or email already taken");
        }

        User user = new User();
        user.setFirstName(req.firstName());
        user.setSecondName(req.secondName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(Instant.now());
        
        var client = Client.builder()
        .firstName("")
        .lastName("")
            .phone("")
            .email("")
            .createdAt(Instant.now())
            .accounts(List.of())
            .build();
        
        user.setClientId(clientService.create(client).getId());
        
        userRepository.save(user);

    }

    public Dto.AuthResponse login(Dto.LoginRequest request, HttpServletRequest httpRequest) {
        // Step 1: Authenticate credentials
        Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
        new StringBuilder(request.firstName())
                    .append("/")
                    .append(request.secondName()).toString(),
        request.password()
        )
        );

        // Step 2: Load user details
        User user = userRepository.findByFirstNameAndSecondName(request.firstName(), request.secondName())
        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Step 5: Generate tokens
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String accessToken = jwtUtil.generateAccessToken(
        userDetails,
        user.getClientId()
        );

        String refreshToken = refreshTokenService.createRefreshToken(
        user.getId(),
        httpRequest.getRemoteAddr(),
        httpRequest.getHeader("User-Agent"));

        RefreshToken entity = new RefreshToken();

        entity.setId(UUID.randomUUID());
        entity.setUserId(user.getId());
        entity.setTokenHash(refreshToken);
        entity.setIssuedAt(Instant.now());
        entity.setFamily(UUID.randomUUID());
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        entity.setIpAddress(httpRequest.getRemoteAddr());
        entity.setUserAgent(httpRequest.getHeader("User-Agent"));

        refreshTokenService.save(entity);

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

    public Dto.AuthResponse refresh(Dto.RefreshRequest request, HttpServletRequest httpRequest) {
        // Validate refresh token
        return refreshTokenService.rotate(
            request.refreshToken(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent")
        );
    }
}
