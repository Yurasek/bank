package org.example;

import java.math.BigDecimal;

public class WaitNotifySync {
    private String packet;
    private boolean isSending = true;
    BigDecimal digit = BigDecimal.ZERO;

    public synchronized String receiving(){
        while(isSending){
            try {
                wait();
            } catch (InterruptedException e) {
                System.out.printf("Thread has been interrupted: %s", Thread.currentThread().getName());
                throw new RuntimeException(e);
            }
        }
        isSending = true;
        notifyAll();
        String returnPacket = this.packet;
        return returnPacket;
    }

    public synchronized void send(String packet){
        while(!isSending){
            try {
                wait();
            } catch (InterruptedException e) {
                System.out.printf("Thread has been interrupted: %s", Thread.currentThread().getName());
                throw new RuntimeException(e);
            }
        }
        isSending = false;
        notifyAll();
        this.packet = packet;
    }
}
