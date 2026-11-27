import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.*;
import org.example.User;
import org.example.*;
import org.example.enums.*;
import org.example.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

import javax.crypto.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive security and authentication test suite.
 * Organized by component with nested test classes.
 */
@SpringBootTest(classes = BankApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@Import(JwtUtil.class)
public class BankingSecurityTestSuite {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private AccountService accountService;

    @Autowired
    private JwtUtil jwtUtil;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_CLIENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final String TEST_LOGIN = "test@example.com";
    private static final String FIRST_NAME = "alice";
    private static final String SECOND_NAME = "doe";
    private static final String TEST_PASSWORD = "password123";

    // ═════════════════════════════════════════════════════════════════
    // NESTED: JwtUtil Tests
    // ═════════════════════════════════════════════════════════════════


        @Test
        @DisplayName("generateToken_validInputs_returnsValidJwt")
        void generateToken_validInputs_returnsValidJwt() {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        }

        @Test
        @DisplayName("generateToken_containsCorrectClaims")
        void generateToken_containsCorrectClaims() {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            var claims = jwtUtil.parseToken(token);

            assertThat(claims.getSubject()).isEqualTo(TEST_LOGIN);
            assertThat(claims.get("clientId", String.class)).isEqualTo(TEST_CLIENT_ID.toString());
            assertThat(claims.get("type", String.class)).isEqualTo("ACCESS");
//            @SuppressWarnings("unchecked")
//            List<String> roles = claims.get("roles", List.class);
//            assertThat(roles).containsExactly("ROLE_CLIENT", "ROLE_PREMIUM");
        }

        @Test
        @DisplayName("generateToken_hasValidExpiration")
        void generateToken_hasValidExpiration() {
            Instant before = Instant.now();
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );
            Instant after = Instant.now();

            var claims = jwtUtil.parseToken(token);
            Instant expiration = claims.getExpiration().toInstant();

            assertThat(expiration).isAfter(before.plus(14, ChronoUnit.MINUTES));
            assertThat(expiration).isBefore(after.plus(16, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("generateToken_hasValidIssuedAt")
        void generateToken_hasValidIssuedAt() {
            Instant before = Instant.now();
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );
            Instant after = Instant.now();

            var claims = jwtUtil.parseToken(token);
            Instant issuedAt = claims.getIssuedAt().toInstant();

            assertThat(issuedAt).isAfterOrEqualTo(before.minusSeconds(1));
            assertThat(issuedAt).isBeforeOrEqualTo(after.plusSeconds(1));
        }

        @Test
        @DisplayName("parseToken_invalidSignature_throwsJwtException")
        void parseToken_invalidSignature_throwsJwtException() {
            String validToken = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            // Tamper with signature
            String tamperedToken = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "invalid";

            assertThatThrownBy(() -> jwtUtil.parseToken(tamperedToken))
            .isInstanceOfAny(SignatureException.class, JwtException.class);
        }

        @Test
        @DisplayName("parseToken_malformedToken_throwsMalformedJwtException")
        void parseToken_malformedToken_throwsMalformedJwtException() {
            assertThatThrownBy(() -> jwtUtil.parseToken("not.a.jwt"))
            .isInstanceOfAny(MalformedJwtException.class, JwtException.class);

            assertThatThrownBy(() -> jwtUtil.parseToken("completely-invalid"))
            .isInstanceOfAny(MalformedJwtException.class, IllegalArgumentException.class);
        }


    // ═════════════════════════════════════════════════════════════════
    // NESTED: JwtAuthenticationFilter Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("JWT Authentication Filter")
    class JwtAuthenticationFilterTests {

        @Test
        @DisplayName("doFilter_protectedPathWithoutToken_returns403")
        void doFilter_protectedPathWithoutToken_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/my"))
            .andExpect(status().isForbidden()); // 403 from Spring Security
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: Security Configuration Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Security Configuration")
    class SecurityConfigTests {

        @Test
        @DisplayName("protectedPaths_noAuth_blocked")
        void protectedPaths_noAuth_blocked() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/my"))
            .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/transfers/history"))
            .andExpect(status().isForbidden());
        }

//        @Test
//        @DisplayName("csrf_disabled_forApi")
//        void csrf_disabled_forApi() throws Exception {
//            // POST without CSRF token should work (if we had valid auth)
//            // This verifies CSRF is not required for API endpoints
//            mockMvc.perform(post("/api/v1/auth/login")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{}"))
//            .andExpect(status().isBadRequest()); // 400, not 403 (CSRF)
//        }
    }

    // ═════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════════════════

    private UserDetails createUserDetails(String username) {
        return new org.springframework.security.core.userdetails.User(
        username,
        "",
        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );
    }

    private Authentication createAuthentication() {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
        createUserDetails(TEST_LOGIN),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );
    }

    private User createTestUser() {
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setFirstName(FIRST_NAME);
        user.setSecondName(SECOND_NAME);
        user.setClientId(TEST_CLIENT_ID);
        user.setStatus(UserStatus.ACTIVE);
//        user.setMfaEnabled(false);
        return user;
    }

    private String createExpiredToken() {
        // Use reflection or test-specific JwtUtil method to create expired token
        // Alternative: mock time or use a test key with past expiration
        SecretKey testKey = Keys.hmacShaKeyFor("test-secret-32-chars-long!!!".getBytes());

        return Jwts.builder()
        .subject(TEST_LOGIN)
        .claim("clientId", TEST_CLIENT_ID.toString())
        .claim("type", "ACCESS")
        .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
        .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
        .signWith(testKey)
        .compact();
    }


}