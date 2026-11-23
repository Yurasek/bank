import jakarta.persistence.EntityManager;
import org.example.*;
import org.example.dto.Dto;
import org.example.enums.AccountStatus;
import org.example.enums.AccountType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = BankApplication.class)
@TestPropertySource(locations = "classpath:application-test.properties")
//@Import(TestcontainersConfiguration.class)
//@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    private volatile int RCVolatileCounter = 0;
    private int RCCounter = 1;
//    private int RCCounter = 0;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferHistoryRepository transferHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clean() {
        transferHistoryRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @AfterAll
    void lastClean() {
        transferHistoryRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Account createAccount(String number, String balance) {

        Optional<Client> client = clientRepository.findById(UUID.fromString("827e9353-3fcf-4734-935d-902c6a706987"));

        return accountRepository.save(Account.builder()
        .accountNumber(number)
        .client(client.get())
        .balance(new BigDecimal(balance))
        .accountType(AccountType.DEBIT)
        .status(AccountStatus.ACTIVE)
        .currency("USD")
        .updatedAt(LocalDateTime.now())
        .build());
    }

    private Account get(UUID id) {
        return accountRepository.findById(id).orElseThrow();
    }

    // ============ BASIC FUNCTIONALITY ============

    @Test
    void shouldTransferSuccessfully() {
        Account a = createAccount("A", "1000.00");
        Account b = createAccount("B", "500.00");

        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("300.00")));

        assertThat(get(a.getId()).getBalance()).isEqualByComparingTo("700.00");
        assertThat(get(b.getId()).getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void shouldTransferReverseDirection() {
        Account a = createAccount("A", "1000.00");
        Account b = createAccount("B", "500.00");

        accountService.transfer(new Dto.TransferRequest(b.getId().toString(), a.getId().toString(), new BigDecimal("200.00")));

        assertThat(get(a.getId()).getBalance()).isEqualByComparingTo("1200.00");
        assertThat(get(b.getId()).getBalance()).isEqualByComparingTo("300.00");
    }

    // ============ VALIDATION ============

    @Test
    void shouldRejectNegativeAmount() {
        Account a = createAccount("A", "1000.00");
        Account b = createAccount("B", "500.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("-100.00")))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("positive");
    }

    @Test
    void shouldRejectZeroAmount() {
        Account a = createAccount("A", "1000.00");
        Account b = createAccount("B", "500.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), BigDecimal.ZERO))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("positive");
    }

    @Test
    void shouldRejectSameAccount() {
        Account a = createAccount("A", "1000.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), a.getId().toString(), new BigDecimal("100.00")))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("same account");
    }

    @Test
    void shouldRejectNonExistentSource() {
        Account b = createAccount("B", "500.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(UUID.randomUUID().toString(), b.getId().toString(), new BigDecimal("100.00")))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("not found");
    }

    @Test
    void shouldRejectNonExistentDestination() {
        Account a = createAccount("A", "1000.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), UUID.randomUUID().toString(), new BigDecimal("100.00")))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("not found");
    }

    @Test
    void shouldRejectInsufficientFunds() {
        Account a = createAccount("A", "100.00");
        Account b = createAccount("B", "500.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("200.00")))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("Insufficient funds");

        assertThat(get(a.getId()).getBalance()).isEqualByComparingTo("100.00");
        assertThat(get(b.getId()).getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldRejectInactiveAccount() {
        Account a = accountRepository.save(Account.builder().accountNumber("A").balance(new BigDecimal("1000.00")).build());
        Account b = createAccount("B", "500.00");

        assertThatThrownBy(() ->
        accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("100.00")))
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("inactive");
    }

    // ============ CONCURRENCY (CRITICAL) ============

    @Test
    void shouldHandleConcurrentOppositeTransfersWithoutDeadlock() throws Exception {
        Account a = createAccount("A", "10000.00");
        Account b = createAccount("B", "10000.00");

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("10.00")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    accountService.transfer(new Dto.TransferRequest(b.getId().toString(), a.getId().toString(), new BigDecimal("5.00")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();

        BigDecimal total = get(a.getId()).getBalance().add(get(b.getId()).getBalance());
        assertThat(total).isEqualByComparingTo("20000.00");
    }

    @Test
    void shouldHandleConcurrentTransfersFromSameSource() throws Exception {
        Account source = createAccount("SRC", "10000.00");
        Account d1 = createAccount("D1", "0.00");
        Account d2 = createAccount("D2", "0.00");


        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            UUID dest = i % 2 == 0 ? d1.getId() : d2.getId();
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    accountService.transfer(new Dto.TransferRequest(source.getId().toString(), dest.toString(), new BigDecimal("100.00")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(get(source.getId()).getBalance()).isEqualByComparingTo("8000.00");
    }

    @Test
    void RaceConditionTransfersFromSameSource() throws Exception {

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
//                    RCVolatileCounter++;
                    for (int j = 0; j < 1000; j++) {
                        RCCounter = RCCounter * 10;
                        RCCounter = RCCounter / 10;
                        RCCounter = RCCounter + 100;
                        RCCounter = RCCounter - 100;
                    }
                    System.out.println("Thread " + Thread.currentThread().getName()
                    + " finished, counter = " + RCCounter);
//                    accountService.transfer(new Dto.TransferRequest(source.getId().toString(), dest.toString(), new BigDecimal("100.00")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        System.out.printf("Counter: %s, Volatile Counter: \n", RCCounter);
        System.out.flush();
        executor.shutdown();

        assertThat(RCCounter).isEqualByComparingTo(1);
//        assertThat(RCVolatileCounter).isEqualByComparingTo(threads);
    }

    @Test
    void BubbleSortTest() {
        Integer[] array = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        List<Integer> list = Arrays.asList(array);

        Collections.shuffle(list);
        array = list.toArray(Integer[]::new);

        for (int i = 0; i < array.length; i++) {
            System.out.print(array[i]);
            System.out.print(", ");
        }

        System.out.println();
        System.out.println();

//        bubbleSort(array);

        for (int i = 0; i < array.length; i++) {
            System.out.print(array[i]);
            System.out.print(", ");
        }
    }

    @Test
    void TimerThreadTest(){
        new Thread(() -> {
            for (int i = 0; i<60; i++){
                try {
                    TimeUnit.SECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.printf("Sec passed: %s", i);
            }
        }).start();
    }

    @Test
    void virtualThreadTest(){
        Thread test = Thread.ofVirtual().start(()->{
            System.out.println("LAlcmdklc");
        });
    }

    private static void bubbleSort(Integer[] array) {
        for (int i = 0; i < array.length; i++) {
            for (int j = 0; j < array.length - i - 1; j++) {
                int swap = 0;
                if (array[j] > array[j + 1]) {
                    swap = array[j + 1];
                    array[j + 1] = array[j];
                    array[j] = swap;
                }
            }
        }
    }

    @Test
    void shouldHandleConcurrentInsufficientFundsRace() throws Exception {
        Account a = createAccount("A", "500.00");
        Account b = createAccount("B", "0.00");

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("100.00")));
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }

        latch.countDown();

        int success = 0;
        int fail = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(30, TimeUnit.SECONDS)) success++;
            else fail++;
        }
        executor.shutdown();

        assertThat(success).isEqualTo(5);
        assertThat(fail).isEqualTo(5);
        assertThat(get(a.getId()).getBalance()).isEqualByComparingTo("0.00");
        assertThat(get(b.getId()).getBalance()).isEqualByComparingTo("500.00");
    }

    // ============ ATOMICITY ============

    @Test
    void shouldNotSeeUncommittedChanges() throws Exception {
        Account a = createAccount("A", "1000.00");
        Account b = createAccount("B", "500.00");

        CountDownLatch txStarted = new CountDownLatch(1);
        CountDownLatch txCanCommit = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> tx1 = executor.submit(() -> {
            TransactionTemplate t = new TransactionTemplate(new JpaTransactionManager(entityManager.getEntityManagerFactory()));
            t.execute(s -> {
                accountService.transfer(new Dto.TransferRequest(a.getId().toString(), b.getId().toString(), new BigDecimal("200.00")));
                txStarted.countDown();
                try {
                    txCanCommit.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        txStarted.await();

        Future<BigDecimal[]> tx2 = executor.submit(() -> {
            TransactionTemplate t = new TransactionTemplate(new JpaTransactionManager(entityManager.getEntityManagerFactory()));
            t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return t.execute(s -> new BigDecimal[]{
            get(a.getId()).getBalance(),
            get(b.getId()).getBalance()
            });
        });

        BigDecimal[] balances = tx2.get(5, TimeUnit.SECONDS);
        assertThat(balances[0]).isEqualByComparingTo("1000.00");
        assertThat(balances[1]).isEqualByComparingTo("500.00");

        txCanCommit.countDown();
        tx1.get(5, TimeUnit.SECONDS);
        executor.shutdown();
    }
}