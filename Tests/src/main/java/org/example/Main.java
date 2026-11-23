package org.example;

import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;

class BankAccount {
    private BigDecimal balance = BigDecimal.ZERO;

    public synchronized void withdraw(BigDecimal amount){
        if(balance.compareTo(amount) >= 0 ){
            balance.subtract(amount);
        } else {
            System.out.printf("The balance '%s' is lower than withdraw amount: %s \n", balance, amount);
        }
    }

    public synchronized void deposit(BigDecimal amount){
        this.balance = amount;
    }

    public synchronized BigDecimal balance(){
        return this.balance;
    }
}
public class Main {
    public static void main(String[] args) throws InterruptedException {

        String kek = "dfvdfv";
        BankAccount account = new BankAccount();

        // Example Thread to Deposit
        Thread t1 = new Thread(() -> {
            for (Long i = 0L; i < 1000; i++) {
                account.deposit(BigDecimal.valueOf(i));
            }
        });

        Thread t2 = new Thread(() -> {
            for (Long i = 0L; i < 1000; i++) {
                account.withdraw(BigDecimal.valueOf(i));
            }
        });

        Thread t3 = new Thread(() -> {
            for (Long i = 0L; i < 1000; i++) {
                account.deposit(BigDecimal.valueOf(i));
            }
        });
        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        System.out.printf("Balance: %s", account.balance());

    }

    private static void timer() {
        Thread thread = new Thread(() -> {
            System.out.printf("Thread state: %s%n \n", Thread.currentThread().getState());
            for (int i = 0; i < 5; i++) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.printf("Thread state: %s%n \n", Thread.currentThread().getState());
                System.out.printf("Sec passed: %s%n \n", i);
            }
        });
        System.out.printf("Thread state: %s%n \n", thread.getState());
        thread.start();
    }
}