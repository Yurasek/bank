

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.*;
import org.example.dto.Dto;
import org.example.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests over real HTTP + real DB:
 *   1. login -> transfer
 *   2. signup -> login -> transfer
 *
 * Uses Testcontainers PostgreSQL with the real Flyway migrations,
 * real AuthenticationManager, real BCrypt, real JwtUtil, real services.
 */
@SpringBootTest(classes = BankApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
//@Import(AuthFlowIntegrationTest.TestContainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired TransferHistoryRepository transferHistoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

//    @TestConfiguration(proxyBeanMethods = false)
//    static class TestContainersConfig {
//        @Bean
//        @ServiceConnection
//        PostgreSQLContainer<?> postgresContainer() {
//            return new PostgreSQLContainer<>("postgres:15")
//            .withDatabaseName("testdb")
//            .withUsername("test")
//            .withPassword("test");
//        }
//    }

    private static final String FIRST_NAME = "alice";
    private static final String SECOND_NAME = "doe";
    private static final String PASSWORD = "password123";

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        transferHistoryRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
    }

    // ═════════════════════════════════════════════════════════════════
    // 1. login -> transfer
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("login_then_transfer_succeeds")
    void login_then_transfer_succeeds() throws Exception {
        // ── Arrange: seed a user (with clientId!) and two accounts ──
        Client client = seedClient();
        seedUser(FIRST_NAME, SECOND_NAME, PASSWORD, client.getId());
        Account from = seedAccount(client, "1000.0000");
        Account to   = seedAccount(client, "1000.0000");

        // ── Act 1: login ──
        Dto.AuthResponse auth = login(FIRST_NAME, SECOND_NAME, PASSWORD);
        assertThat(auth.accessToken()).isNotNull();
        assertThat(auth.status()).isEqualTo(Dto.AuthStatus.SUCCESS);

        // ── Act 2: transfer 100 from -> to ──
        mockMvc.perform(post("/api/client/transfer")
        .header("Authorization", "Bearer " + auth.accessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new Dto.TransferRequest(
        from.getId().toString(),
        to.getId().toString(),
        new BigDecimal("100.00")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(from.getId().toString()));

        // ── Assert: balances moved, history recorded ──
        assertThat(accountRepository.findById(from.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("900.0000");
        assertThat(accountRepository.findById(to.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("1100.0000");
        assertThat(transferHistoryRepository.findAll())
        .singleElement()
        .satisfies(h -> assertThat(h.getTransferState()).isEqualTo(TransferState.SUCCESS));
    }

    // ═════════════════════════════════════════════════════════════════
    // 2. signup -> login -> transfer
    // ═════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("signup_then_login_then_transfer_succeeds")
    void signup_then_login_then_transfer_succeeds() throws Exception {
        String first = "bob";
        String second = "boe";
        String password = "bobPassw0rd!";

        // ── Act 1: signup ──
        mockMvc.perform(post("/api/v1/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"firstName\":\"" + first  + "\",\"secondName\":\""+ second + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk());

        // ── Act 2: login with the fresh credentials ──
        Dto.AuthResponse auth = login(first, second, password);
        assertThat(auth.clientId()).isNotNull();

        // ── Arrange: the "bank" opens accounts for the new client ──
        Account from = seedAccount(clientRepository.findById(auth.clientId()).orElseThrow(), "500.0000");
        Account to   = seedAccount(clientRepository.findById(auth.clientId()).orElseThrow(), "0.0000");

        // ── Act 3: transfer ──
        mockMvc.perform(post("/api/client/transfer")
        .header("Authorization", "Bearer " + auth.accessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new Dto.TransferRequest(
        from.getId().toString(),
        to.getId().toString(),
        new BigDecimal("250.00")))))
        .andExpect(status().isOk());

        assertThat(accountRepository.findById(from.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("250.0000");
        assertThat(accountRepository.findById(to.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("250.0000");
    }

    // ═════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════
    private Dto.AuthResponse login(String firstName, String secondName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new Dto.LoginRequest(firstName, secondName, password, java.util.Optional.empty()))))
        .andExpect(status().isOk())
        .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), Dto.AuthResponse.class);
    }

    private Client seedClient() {
        Client client = new Client();
        client.setFirstName("Alice");
        client.setLastName("Anderson");
        client.setEmail(UUID.randomUUID() + "@example.com"); // unique per test
        return clientRepository.save(client);
    }

    private User seedUser(String firstName, String secondName,  String rawPassword, UUID clientId) {
        User user = new User();
        user.setFirstName(firstName);
        user.setSecondName(secondName);
        user.setPasswordHash(passwordEncoder.encode(rawPassword)); // real BCrypt, no mocks
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(Instant.now());
        user.setClientId(clientId);
        return userRepository.save(user);
    }

    private Account seedAccount(Client client, String balance) {
        Account account = new Account();
        account.setClient(client);
        account.setAccountType(AccountType.DEBIT);
        account.setAccountNumber(UUID.randomUUID().toString().replace("-", "")); // unique
        account.setBalance(new BigDecimal(balance));
        account.setCurrency("USD");
        account.setStatus(AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }
}