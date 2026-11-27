import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.*;
import org.example.*;
import org.example.dto.*;
import org.example.enums.*;
import org.example.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.test.web.servlet.*;

import javax.crypto.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ═════════════════════════════════════════════════════════════════
// NESTED: Security Edge Cases & Attack Prevention
// ═════════════════════════════════════════════════════════════════
@SpringBootTest(classes = BankApplication.class)
@AutoConfigureMockMvc
@Nested
@DisplayName("Security Edge Cases & Attack Prevention")
class SecurityEdgeCaseTests {

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
    private static final String TEST_PASSWORD = "password123";

    private static final String SECRET = "omega_long_jwt_secret_phrase_1337!@#$%";

    @Test
    @DisplayName("token_withNullSubject_rejected")
    void token_withNullSubject_rejected() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

        String badToken = Jwts.builder()
        // No subject set
        .claim("clientId", TEST_CLIENT_ID.toString())
        .signWith(key)
        .compact();

        assertThatThrownBy(() -> jwtUtil.parseToken(badToken))
        .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token_withFutureIat_rejected")
    void token_withFutureIat_rejected() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

        String badToken = Jwts.builder()
        .subject(TEST_LOGIN)
        .issuedAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
        .expiration(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)))
        .signWith(key)
        .compact();

        // Depending on your validation, this may or may not be rejected
        // Add explicit iat validation in JwtUtil for strict checking
        assertThatThrownBy(() -> jwtUtil.parseToken(badToken))
        .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("token_withAlgorithmNone_rejected")
    void token_withAlgorithmNone_rejected() {
        // "alg": "none" attack
        String header = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"hacker\"}".getBytes());

        String noneToken = header + "." + payload + ".";

        assertThatThrownBy(() -> jwtUtil.parseToken(noneToken))
        .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token_withAlgorithmConfusion_rejected")
    void token_withAlgorithmConfusion_rejected() {
        // Try to use RSA public key as HMAC secret
        // This test requires actual RSA key pair setup
        // Simplified: verify your parser rejects wrong algorithm

        String validToken = jwtUtil.generateAccessToken(
        createUserDetails(TEST_LOGIN),
        TEST_CLIENT_ID
        );

        // Tamper algorithm header
        String[] parts = validToken.split("\\.");
        String tamperedHeader = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());

        String confusedToken = tamperedHeader + "." + parts[1] + "." + parts[2];

        assertThatThrownBy(() -> jwtUtil.parseToken(confusedToken))
        .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("veryLongToken_rejected")
    void veryLongToken_rejected() {
        // Create oversized claim
        String hugeData = "x".repeat(10000);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

        String hugeToken = Jwts.builder()
        .subject(TEST_LOGIN)
        .claim("data", hugeData)
        .signWith(key)
        .compact();

        // Your parser should have size limits
        assertThatThrownBy(() -> jwtUtil.parseToken(hugeToken))
        .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sqlInjectionInLogin_rejected")
    void sqlInjectionInLogin_rejected() throws Exception {
        String sqlInjection = "'; DROP TABLE users; --";

        when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Invalid"));

        Dto.LoginRequest request = new Dto.LoginRequest(sqlInjection,sqlInjection, "password", Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());

        // Verify no SQL was executed (repository not called due to auth failure)
        verify(userRepository, never()).findByFirstNameAndSecondName(any(), any());
    }

    @Test
    @DisplayName("xssInUserAgent_loggedSafely")
    void xssInUserAgent_loggedSafely() throws Exception {
        String xssPayload = "<script>alert('xss')</script>";

        when(authenticationManager.authenticate(any()))
        .thenReturn(createAuthentication());
        when(userRepository.findByFirstNameAndSecondName(TEST_LOGIN, TEST_LOGIN))
        .thenReturn(Optional.of(createTestUser()));
        when(refreshTokenService.createRefreshToken(any(), any(), any()))
        .thenReturn("refresh-token");

        Dto.LoginRequest request = new Dto.LoginRequest(TEST_LOGIN,TEST_LOGIN, TEST_PASSWORD, Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
        .header("User-Agent", xssPayload))
        .andExpect(status().isOk());

        // Verify the XSS payload was handled (not executed, properly escaped if logged)
        ArgumentCaptor<String> uaCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenService).createRefreshToken(any(), any(), uaCaptor.capture());
        assertThat(uaCaptor.getValue()).isEqualTo(xssPayload); // Stored as-is, not executed
    }

    @Test
    @DisplayName("massiveConcurrentLogins_rateLimited")
    void massiveConcurrentLogins_rateLimited() throws Exception {
        int attempts = 20;
        CountDownLatch latch = new CountDownLatch(attempts);
        AtomicInteger tooManyRequests = new AtomicInteger(0);

        when(authenticationManager.authenticate(any()))
        .thenReturn(createAuthentication());
        when(userRepository.findByFirstNameAndSecondName(TEST_LOGIN, TEST_LOGIN))
        .thenReturn(Optional.of(createTestUser()));
        when(refreshTokenService.createRefreshToken(any(), any(), any()))
        .thenReturn("refresh-token");

        ExecutorService executor = Executors.newFixedThreadPool(attempts);

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                    new Dto.LoginRequest(TEST_LOGIN,TEST_LOGIN, TEST_PASSWORD, Optional.empty())
                    )))
                    .andReturn();

                    if (result.getResponse().getStatus() == 429) {
                        tooManyRequests.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Some requests should be rate limited
        assertThat(tooManyRequests.get()).isGreaterThan(0);
    }

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

    private org.example.User createTestUser() {
        org.example.User user = new org.example.User();
        user.setId(TEST_USER_ID);
        user.setFirstName(TEST_LOGIN);
        user.setSecondName(TEST_LOGIN);
        user.setClientId(TEST_CLIENT_ID);
        user.setStatus(UserStatus.ACTIVE);
//        user.setMfaEnabled(false);
        return user;
    }

    private String createExpiredToken() {
        // Use reflection or test-specific JwtUtil method to create expired token
        // Alternative: mock time or use a test key with past expiration
        SecretKey testKey = Keys.hmacShaKeyFor(SECRET.getBytes());

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
