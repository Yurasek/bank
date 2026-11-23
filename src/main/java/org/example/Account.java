package org.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import org.example.enums.AccountStatus;
import org.example.enums.AccountType;
import org.springframework.data.annotation.Version;

import javax.naming.InsufficientResourcesException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "account_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountType accountType; // DEBIT, CREDIT, JOB

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ACTIVE;

//    @Version // ← concurrency guard, see section 2
//    private Long version;

    // nullable type-specific fields from your Option A schema
    @Column(name = "daily_withdrawal_limit", precision = 19, scale = 4)
    private BigDecimal dailyWithdrawalLimit;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "employer_name")
    private String employerName;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void debit(BigDecimal amount) {
        if(this.balance.compareTo(amount) < 0){
            throw new RuntimeException(
                    String.format("The balance = %s lower than amount = %s",
                            this.balance, amount
                    )
            );
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void credit(BigDecimal amount){
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }
}
