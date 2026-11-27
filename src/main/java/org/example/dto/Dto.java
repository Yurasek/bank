package org.example.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Dto {

    public record ClientResponse(
            UUID id,
            String firstName,
            String lastName,
            String email,
            List<AccountResponse> accounts
    ) {}

    public record AccountResponse(
            Long id,
            String accountType,
            String accountNumber,
            BigDecimal balance,
            String currency
    ) {}

    public record TokenPair(String accessToken, String refreshToken) {}

    public record CreateClientRequest(
            String firstName,
            String lastName,
            String email
    ) {}

    public record LoginRequest(
    String firstName,
    String secondName,
    String password,
    Optional<String> mfaCode
    ) {}

    public record AuthResponse(
    String accessToken,
    String refreshToken,
    AuthStatus status,
    UUID clientId
    ) {}

    public record RefreshRequest(
     String refreshToken
    ) {}

    public enum AuthStatus { SUCCESS, MFA_REQUIRED, PASSWORD_EXPIRED }

    public record TransferRequest(
            String fromAccountId,
            String toAccountId,
            BigDecimal amount
    ) {}

    public record ClientDto(
            UUID id,
            String firstName,
            String lastName,
            String email,
            List<AccountDto> accounts
    ) {}

    public record AccountDto(
            UUID id,
            String accountType,
            String accountNumber,
            BigDecimal balance,
            String currency
    ) {}
}
