package org.example;

import jakarta.persistence.*;
import lombok.*;
import org.example.enums.TransferState;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "transfer_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "from_account", nullable = false)
    public String accountFrom;

    @Column(name = "to_account",nullable = false)
    public String accountTo;

    @Column(nullable = false)
    public BigDecimal amount;

    @Column(name = "from_balance_before",nullable = false)
    public BigDecimal accountFromAmount;

    @Column(name = "to_balance_before",nullable = false)
    public BigDecimal accountToAmount;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "failure_reason", length = 500)
    public String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false)
    public TransferState transferState;
}
