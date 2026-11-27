package org.example.filter;

import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.example.*;
import org.example.User;
import org.example.util.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.*;
import org.springframework.web.filter.*;

import java.io.*;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
//    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    // Endpoints that don't need JWT
    private static final List<String> PUBLIC_PATHS = List.of(
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/register",
    "/actuator/health"
    );

    @Override
    protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip JWT check for public paths
        String requestPath = request.getRequestURI();
        if (isPublicPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token from header
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("No JWT token found in request to {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            // Validate token structure and signature
            if (!jwtUtil.isTokenValid(jwt)) {
                log.warn("Invalid JWT token");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract claims
            String username = jwtUtil.extractUsername(jwt);
            UUID clientId = jwtUtil.extractClientId(jwt);
            List<String> roles = jwtUtil.extractRoles(jwt);

            // Optional: check if user still exists and is active
            // This hits DB but ensures token isn't for deleted user
            Optional<User> userDetails = userRepository.findByFirstNameAndSecondName(username.split("/")[0], username.split("/")[1]);
            if (userDetails == null) {
                log.warn("User {} not found", username);
                filterChain.doFilter(request, response);
                return;
            }

            // Build authentication object
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
            UUID.randomUUID(), // or extract from token if you put userId there
            clientId,
            username,
            List.of(),
            true // or extract from token
            );

            UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(
            authenticatedUser,  // principal
            null,              // credentials (JWT already validated)
            authenticatedUser.getAuthorities()
            );

            // Add request details for audit logging
            authToken.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (JwtException e) {
            log.error("JWT processing failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
