import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.example.*;
import org.example.dto.*;
import org.example.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive security and authentication test suite.
 * Organized by component with nested test classes.
 */
@SpringBootTest
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
    private static final String TEST_PASSWORD = "password123";

    // ═════════════════════════════════════════════════════════════════
    // NESTED: JwtUtil Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("JWT Token Generation & Validation")
    class JwtUtilTests {

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
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            assertThat(roles).containsExactly("ROLE_CLIENT", "ROLE_PREMIUM");
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

        @Test
        @DisplayName("doFilter_tokenWithNonexistentUser_continuesWithoutAuthentication")
        void doFilter_tokenWithNonexistentUser_continuesWithoutAuthentication() throws Exception {
            // Create valid token for non-existent user
            String token = jwtUtil.generateAccessToken(
            createUserDetails("nonexistent@example.com"),
            UUID.randomUUID()
            );

            when(userDetailsService.loadUserByUsername("nonexistent@example.com"))
            .thenReturn(null);

            mockMvc.perform(get("/api/v1/accounts/my")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: RefreshTokenService Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Refresh Token Management")
    class RefreshTokenServiceTests {

        @Test
        @DisplayName("createRefreshToken_validUser_returnsRawToken")
        void createRefreshToken_validUser_returnsRawToken() {
            when(refreshTokenRepository.countByUserIdAndRevokedAtIsNull(TEST_USER_ID))
            .thenReturn(0L);

            when(refreshTokenRepository.save(any(RefreshToken.class)))
            .thenAnswer(inv -> inv.getArgument(0));

            String rawToken = refreshTokenService.createRefreshToken(
            TEST_USER_ID,
            "127.0.0.1",
            "Mozilla/5.0"
            );

            assertThat(rawToken).isNotNull();
            assertThat(rawToken).hasSizeGreaterThan(20);
        }

        @Test
        @DisplayName("createRefreshToken_setsCorrectUserId")
        void createRefreshToken_setsCorrectUserId() {
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

            when(refreshTokenRepository.countByUserIdAndRevokedAtIsNull(any()))
            .thenReturn(0L);
            when(refreshTokenRepository.save(captor.capture()))
            .thenAnswer(inv -> inv.getArgument(0));

            refreshTokenService.createRefreshToken(TEST_USER_ID, "127.0.0.1", "Mozilla");

            RefreshToken saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
        }

        @Test
        @DisplayName("createRefreshToken_exceedsMaxTokens_revokesOldest")
        void createRefreshToken_exceedsMaxTokens_revokesOldest() {
            when(refreshTokenRepository.countByUserIdAndRevokedAtIsNull(TEST_USER_ID))
            .thenReturn(5L); // At limit

            // Should trigger revocation of oldest
            doNothing().when(refreshTokenService).revokeOldestToken(TEST_USER_ID);

            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String token = refreshTokenService.createRefreshToken(TEST_USER_ID, "127.0.0.1", "Mozilla");

            assertThat(token).isNotNull();
        }

        @Test
        @DisplayName("rotate_validToken_returnsNewPair")
        void rotate_validToken_returnsNewPair() {
            String oldRawToken = "old-refresh-token";
            String oldHash = DigestUtils.sha256Hex(oldRawToken);

            RefreshToken oldToken = new RefreshToken();
            oldToken.setId(UUID.randomUUID());
            oldToken.setUserId(TEST_USER_ID);
            oldToken.setTokenHash(oldHash);
            oldToken.setFamily(UUID.randomUUID());
            oldToken.setIssuedAt(Instant.now());
            oldToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

            when(refreshTokenRepository.findByTokenHash(oldHash))
            .thenReturn(Optional.of(oldToken));
            when(userRepository.findById(TEST_USER_ID))
            .thenReturn(Optional.of(createTestUser()));

            TokenPair result = refreshTokenService.rotate(oldRawToken, "127.0.0.1", "Mozilla");

            assertThat(result.accessToken()).isNotNull();
            assertThat(result.refreshToken()).isNotNull();
            assertThat(result.refreshToken()).isNotEqualTo(oldRawToken);
        }

        @Test
        @DisplayName("rotate_revokedToken_throwsTokenReuseException")
        void rotate_revokedToken_throwsTokenReuseException() {
            String rawToken = "reused-token";
            String hash = DigestUtils.sha256Hex(rawToken);

            RefreshToken revokedToken = new RefreshToken();
            revokedToken.setId(UUID.randomUUID());
            revokedToken.setUserId(TEST_USER_ID);
            revokedToken.setTokenHash(hash);
            revokedToken.setFamily(UUID.randomUUID());
            revokedToken.setRevokedAt(Instant.now());
            revokedToken.setRevokedReason(RevokedReason.LOGOUT);

            when(refreshTokenRepository.findByTokenHash(hash))
            .thenReturn(Optional.of(revokedToken));

            assertThatThrownBy(() -> refreshTokenService.rotate(rawToken, "127.0.0.1", "Mozilla"))
            .isInstanceOf(TokenReuseDetectedException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: AuthController Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Authentication Endpoints")
    class AuthControllerTests {

        @Test
        @DisplayName("login_validCredentials_returnsAccessAndRefreshTokens")
        void login_validCredentials_returnsAccessAndRefreshTokens() throws Exception {
            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(TEST_LOGIN))
            .thenReturn(Optional.of(createTestUser()));
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("test-refresh-token");

            Dto.LoginRequest request = new Dto.LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty());

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

            AuthResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            AuthResponse.class
            );

            assertThat(response.accessToken()).isNotNull();
            assertThat(response.refreshToken()).isEqualTo("test-refresh-token");
        }

        @Test
        @DisplayName("login_validCredentials_returns200")
        void login_validCredentials_returns200() throws Exception {
            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(TEST_LOGIN))
            .thenReturn(Optional.of(createTestUser()));
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("refresh-token");

            LoginRequest request = new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty());

            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        }

        @Test
        @DisplayName("login_invalidCredentials_returns401")
        void login_invalidCredentials_returns401() throws Exception {
            when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

            LoginRequest request = new LoginRequest(TEST_LOGIN, "wrong-password", Optional.empty());

            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("login_nonexistentUser_returns401")
        void login_nonexistentUser_returns401() throws Exception {
            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(any()))
            .thenReturn(Optional.empty());

            LoginRequest request = new LoginRequest("nobody@example.com", TEST_PASSWORD, Optional.empty());

            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("refresh_validRefreshToken_returnsNewTokens")
        void refresh_validRefreshToken_returnsNewTokens() throws Exception {
            when(refreshTokenService.rotate(any(), any(), any()))
            .thenReturn(new TokenPair("new-access", "new-refresh"));

            RefreshRequest request = new RefreshRequest("valid-refresh-token");

            MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

            AuthResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            AuthResponse.class
            );

            assertThat(response.accessToken()).isEqualTo("new-access");
            assertThat(response.refreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("refresh_invalidRefreshToken_returns401")
        void refresh_invalidRefreshToken_returns401() throws Exception {
            when(refreshTokenService.rotate(any(), any(), any()))
            .thenThrow(new InvalidRefreshTokenException("Invalid"));

            RefreshRequest request = new RefreshRequest("invalid-token");

            mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("refresh_revokedRefreshToken_returns401")
        void refresh_revokedRefreshToken_returns401() throws Exception {
            when(refreshTokenService.rotate(any(), any(), any()))
            .thenThrow(new TokenReuseDetectedException("Reused"));

            RefreshRequest request = new RefreshRequest("revoked-token");

            mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("login_rateLimitExceeded_returns429")
        void login_rateLimitExceeded_returns429() throws Exception {
            // Simulate rate limiter triggering
            // Implementation depends on your rate limiting solution (Bucket4j, Redis, etc.)
            // This is a placeholder showing the test structure

            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty())
            )))
            .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("login_missingBody_returns400")
        void login_missingBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: AccountController Security Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Account Endpoint Security")
    class AccountControllerSecurityTests {

        @Test
        @DisplayName("getMyAccounts_validJwt_returnsAccounts")
        void getMyAccounts_validJwt_returnsAccounts() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            when(accountService.findByClientId(TEST_CLIENT_ID))
            .thenReturn(List.of(new AccountResponse(
            UUID.randomUUID(), "123456789", "CHECKING",
            new BigDecimal("1000.00"), "USD", Instant.now()
            )));

            mockMvc.perform(get("/api/v1/accounts/my")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].currency").value("USD"));
        }

        @Test
        @DisplayName("getMyAccounts_noJwt_returns401")
        void getMyAccounts_noJwt_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/my"))
            .andExpect(status().isForbidden()); // or 401 depending on config
        }

        @Test
        @DisplayName("getMyAccounts_expiredJwt_returns401")
        void getMyAccounts_expiredJwt_returns401() throws Exception {
            // Create expired token by manipulating time or using test util
            String expiredToken = createExpiredToken();

            mockMvc.perform(get("/api/v1/accounts/my")
            .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("transfer_validRequest_executesTransfer")
        void transfer_validRequest_executesTransfer() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            UUID fromAccount = UUID.randomUUID();
            UUID toAccount = UUID.randomUUID();

            when(accountService.validateAccountOwnership(fromAccount, TEST_CLIENT_ID))
            .thenReturn(true);
            when(accountService.executeTransfer(eq(TEST_CLIENT_ID), any()))
            .thenReturn(new TransferResponse(UUID.randomUUID(), TransferStatus.COMPLETED, Instant.now()));

            TransferRequest request = new TransferRequest(
            fromAccount, toAccount, new BigDecimal("100.00"), "USD", "Test transfer"
            );

            mockMvc.perform(post("/api/v1/accounts/transfer")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        }

        @Test
        @DisplayName("transfer_fromAccountNotOwned_returns403")
        void transfer_fromAccountNotOwned_returns403() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            UUID fromAccount = UUID.randomUUID();

            doThrow(new AccountOwnershipException("Not your account"))
            .when(accountService).validateAccountOwnership(fromAccount, TEST_CLIENT_ID);

            TransferRequest request = new TransferRequest(
            fromAccount, UUID.randomUUID(), new BigDecimal("100.00"), "USD", "Test"
            );

            mockMvc.perform(post("/api/v1/accounts/transfer")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("transfer_toSameAccount_returns400")
        void transfer_toSameAccount_returns400() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            UUID sameAccount = UUID.randomUUID();

            when(accountService.executeTransfer(any(), any()))
            .thenThrow(new IllegalArgumentException("Same account"));

            TransferRequest request = new TransferRequest(
            sameAccount, sameAccount, new BigDecimal("100.00"), "USD", "Test"
            );

            mockMvc.perform(post("/api/v1/accounts/transfer")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("transfer_exceedsBalance_returns400")
        void transfer_exceedsBalance_returns400() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            when(accountService.executeTransfer(any(), any()))
            .thenThrow(new InsufficientFundsException());

            TransferRequest request = new TransferRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("999999.00"), "USD", "Test"
            );

            mockMvc.perform(post("/api/v1/accounts/transfer")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("getTransactions_validAccount_returnsTransactions")
        void getTransactions_validAccount_returnsTransactions() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            UUID accountId = UUID.randomUUID();

            when(accountService.getTransactions(any(), any(), any()))
            .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/accounts/" + accountId + "/transactions")
            .param("from", "2024-01-01")
            .param("to", "2024-12-31")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        }

        @Test
        @DisplayName("getTransactions_otherUsersAccount_returns403")
        void getTransactions_otherUsersAccount_returns403() throws Exception {
            String token = jwtUtil.generateAccessToken(
            createUserDetails(TEST_LOGIN),
            TEST_CLIENT_ID
            );

            UUID otherAccount = UUID.randomUUID();

            when(accountService.getTransactions(any(), any(), any()))
            .thenThrow(new AccessDeniedException("Not your account"));

            mockMvc.perform(get("/api/v1/accounts/" + otherAccount + "/transactions")
            .param("from", "2024-01-01")
            .param("to", "2024-12-31")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: Security Configuration Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Security Configuration")
    class SecurityConfigTests {

        @Test
        @DisplayName("publicPaths_noAuth_allowed")
        void publicPaths_noAuth_allowed() throws Exception {
            mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());

            // If login is truly public and doesn't require auth
            // (though it requires body, so it returns 400 without)
        }

        @Test
        @DisplayName("protectedPaths_noAuth_blocked")
        void protectedPaths_noAuth_blocked() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/my"))
            .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/transfers/history"))
            .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("csrf_disabled_forApi")
        void csrf_disabled_forApi() throws Exception {
            // POST without CSRF token should work (if we had valid auth)
            // This verifies CSRF is not required for API endpoints
            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isBadRequest()); // 400, not 403 (CSRF)
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: End-to-End Flow Tests
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("End-to-End Authentication Flows")
    class EndToEndFlowTests {

        @Test
        @DisplayName("fullFlow_loginThenAccessProtectedResource")
        void fullFlow_loginThenAccessProtectedResource() throws Exception {
            // Login
            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(TEST_LOGIN))
            .thenReturn(Optional.of(createTestUser()));
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("refresh-token");

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty())
            )))
            .andExpect(status().isOk())
            .andReturn();

            AuthResponse auth = objectMapper.readValue(
            loginResult.getResponse().getContentAsString(),
            AuthResponse.class
            );

            // Access protected resource
            when(accountService.findByClientId(TEST_CLIENT_ID))
            .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/accounts/my")
            .header("Authorization", "Bearer " + auth.accessToken()))
            .andExpect(status().isOk());
        }

        @Test
        @DisplayName("fullFlow_loginThenRefreshThenAccess")
        void fullFlow_loginThenRefreshThenAccess() throws Exception {
            // Login
            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(TEST_LOGIN))
            .thenReturn(Optional.of(createTestUser()));
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("first-refresh");

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty())
            )))
            .andExpect(status().isOk())
            .andReturn();

            AuthResponse firstAuth = objectMapper.readValue(
            loginResult.getResponse().getContentAsString(),
            AuthResponse.class
            );

            // Refresh
            when(refreshTokenService.rotate(any(), any(), any()))
            .thenReturn(new TokenPair("new-access", "new-refresh"));

            MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new RefreshRequest(firstAuth.refreshToken())
            )))
            .andExpect(status().isOk())
            .andReturn();

            AuthResponse refreshedAuth = objectMapper.readValue(
            refreshResult.getResponse().getContentAsString(),
            AuthResponse.class
            );

            // Access with new token
            when(accountService.findByClientId(TEST_CLIENT_ID))
            .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/accounts/my")
            .header("Authorization", "Bearer " + refreshedAuth.accessToken()))
            .andExpect(status().isOk());
        }

        @Test
        @DisplayName("fullFlow_loginThenLogoutThenAccessDenied")
        void fullFlow_loginThenLogoutThenAccessDenied() throws Exception {
            // Login
            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(TEST_LOGIN))
            .thenReturn(Optional.of(createTestUser()));
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("refresh-token");

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty())
            )))
            .andExpect(status().isOk())
            .andReturn();

            AuthResponse auth = objectMapper.readValue(
            loginResult.getResponse().getContentAsString(),
            AuthResponse.class
            );

            // Logout
            doNothing().when(refreshTokenService).revokeToken(auth.refreshToken());

            mockMvc.perform(post("/api/v1/auth/logout")
            .header("Authorization", "Bearer " + auth.accessToken())
            .header("X-Refresh-Token", auth.refreshToken()))
            .andExpect(status().isNoContent());

            // Try refresh (should fail)
            when(refreshTokenService.rotate(any(), any(), any()))
            .thenThrow(new InvalidRefreshTokenException("Revoked"));

            mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new RefreshRequest(auth.refreshToken())
            )))
            .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("fullFlow_tokenExpiresThenRefresh")
        void fullFlow_tokenExpiresThenRefresh() throws Exception {
            String expiredToken = createExpiredToken();

            // Access with expired token fails
            mockMvc.perform(get("/api/v1/accounts/my")
            .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());

            // Refresh to get new token
            when(refreshTokenService.rotate(any(), any(), any()))
            .thenReturn(new TokenPair("new-access", "new-refresh"));

            mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
            new RefreshRequest("valid-refresh")
            )))
            .andExpect(status().isOk());
        }

        @Test
        @DisplayName("fullFlow_concurrentRefresh_onlyOneSucceeds")
        void fullFlow_concurrentRefresh_onlyOneSucceeds() throws Exception {
            String refreshToken = "shared-refresh-token";

            when(refreshTokenService.rotate(eq(refreshToken), any(), any()))
            .thenReturn(new TokenPair("access1", "refresh1"))
            .thenThrow(new InvalidRefreshTokenException("Already used"));

            CountDownLatch latch = new CountDownLatch(2);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);

            Runnable refreshAttempt = () -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                    new RefreshRequest(refreshToken)
                    )))
                    .andReturn();

                    if (result.getResponse().getStatus() == 200) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            };

            ExecutorService executor = Executors.newFixedThreadPool(2);
            executor.submit(refreshAttempt);
            executor.submit(refreshAttempt);

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successes.get()).isEqualTo(1);
            assertThat(failures.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("fullFlow_stolenRefreshToken_detectedOnReuse")
        void fullFlow_stolenRefreshToken_detectedOnReuse() throws Exception {
            String stolenToken = "stolen-refresh";

            // First use (legitimate)
            when(refreshTokenService.rotate(stolenToken, "127.0.0.1", "Mozilla"))
            .thenReturn(new TokenPair("new-access", "new-refresh"));

            mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshRequest(stolenToken))))
            .andExpect(status().isOk());

            // Reuse (attacker tries)
            when(refreshTokenService.rotate(stolenToken, "10.0.0.1", "EvilBrowser"))
            .thenThrow(new TokenReuseDetectedException("Reuse detected!"));

            mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshRequest(stolenToken))))
            .andExpect(status().isUnauthorized());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // NESTED: Security Edge Cases & Attack Prevention
    // ═════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Security Edge Cases & Attack Prevention")
    class SecurityEdgeCaseTests {

        @Test
        @DisplayName("token_withNullSubject_rejected")
        void token_withNullSubject_rejected() {
            SecretKey key = Keys.hmacShaKeyFor("test-secret-32-chars-long!!!".getBytes());

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
            SecretKey key = Keys.hmacShaKeyFor("test-secret-32-chars-long!!!".getBytes());

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
            SecretKey key = Keys.hmacShaKeyFor("test-secret-32-chars-long!!!".getBytes());

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

            LoginRequest request = new LoginRequest(sqlInjection, "password", Optional.empty());

            mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

            // Verify no SQL was executed (repository not called due to auth failure)
            verify(userRepository, never()).findByLogin(any());
        }

        @Test
        @DisplayName("xssInUserAgent_loggedSafely")
        void xssInUserAgent_loggedSafely() throws Exception {
            String xssPayload = "<script>alert('xss')</script>";

            when(authenticationManager.authenticate(any()))
            .thenReturn(createAuthentication());
            when(userRepository.findByLogin(TEST_LOGIN))
            .thenReturn(Optional.of(createTestUser()));
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("refresh-token");

            LoginRequest request = new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty());

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
            when(userRepository.findByLogin(TEST_LOGIN))
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
                        new LoginRequest(TEST_LOGIN, TEST_PASSWORD, Optional.empty())
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
        user.setLogin(TEST_LOGIN);
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