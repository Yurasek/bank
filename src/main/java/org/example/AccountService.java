package org.example;

import lombok.AllArgsConstructor;
import org.example.dto.Dto;
import org.example.enums.TransferState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class AccountService {

    private ClientRepository clientRepository;
    private AccountRepository accountRepository;
    private TransferHistoryRepository transferHistoryRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(Dto.TransferRequest transferRequest) {

        UUID toAccountId = UUID.fromString(transferRequest.toAccountId());
        UUID fromAccountId = UUID.fromString(transferRequest.fromAccountId());

        // 1. SORT UUIDs for consistent lock order
        List<UUID> sortedIds = Stream.of(fromAccountId, toAccountId)
        .sorted()
        .toList();

        // 2. LOCK FIRST — these are the ONLY entities you modify
        Account firstLocked = accountRepository.findByIdWithLock(sortedIds.get(0))
        .orElseThrow(() -> new RuntimeException("Account not found: " + sortedIds.get(0)));

        Account secondLocked = accountRepository.findByIdWithLock(sortedIds.get(1))
        .orElseThrow(() -> new RuntimeException("Account not found: " + sortedIds.get(1)));

        // 3. Determine which is source/destination
        Account fromAccount = firstLocked.getId().equals(fromAccountId) ? firstLocked : secondLocked;
        Account toAccount = firstLocked.getId().equals(fromAccountId) ? secondLocked : firstLocked;

        // 4. NOW create history with LOCKED entities' balances
        TransferHistory transferHistory = TransferHistory.builder()
        .accountTo(toAccountId.toString())
        .accountFrom(fromAccountId.toString())
        .accountToAmount(toAccount.getBalance())      // ← locked entity!
        .accountFromAmount(fromAccount.getBalance())  // ← locked entity!
        .amount(transferRequest.amount())
        .transferState(TransferState.PENDING)
        .createdAt(LocalDateTime.now())
        .build();

        transferHistoryRepository.save(transferHistory);

        // 5. MODIFY LOCKED ENTITIES
        try {
            fromAccount.debit(transferRequest.amount());   // ← locked!
            toAccount.credit(transferRequest.amount());    // ← locked!

            // No need to call save() — @Transactional dirty checking handles it
            // But explicit save is fine too

            System.out.printf("Transfer succeeded: %s -> %s, amount=%s, result source balance = %s%n",
            fromAccount.getAccountNumber(),
            toAccount.getAccountNumber(),
            transferRequest.amount(),
            fromAccount.getBalance()
            );

            transferHistory.setTransferState(TransferState.SUCCESS);
            transferHistoryRepository.save(transferHistory);

        } catch (Exception e) {
            transferHistory.setTransferState(TransferState.FAILED);
            transferHistoryRepository.save(transferHistory);
            throw e;
        }
    }

    public Account findById(UUID id) {
        return accountRepository.findById(id).get();
    }
}
