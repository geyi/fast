package com.kuaidi100.fast.server;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ProducerConsumer02 {

    private static Lock lock = new ReentrantLock();
    private static Condition producerCondition = lock.newCondition();
    private static Condition consumerCondition = lock.newCondition();
    private static final SortRespData[] ITEMS = new SortRespData[10000];

    private static int count, putIndex, takeIndex = 0;

    public static void put(SortRespData obj) {
        lock.lock();
        try {
            while (count == ITEMS.length) {
                producerCondition.await();
            }

            ITEMS[putIndex] = obj;
            if (++putIndex == ITEMS.length) {
                putIndex = 0;
            }
            count++;
            consumerCondition.signalAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public static SortRespData take() {
        lock.lock();
        try {
            while (count == 0) {
                consumerCondition.await();
            }

            SortRespData item = ITEMS[takeIndex];
            if (++takeIndex == ITEMS.length) {
                takeIndex = 0;
            }
            count--;
            producerCondition.signalAll();
            return item;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        } finally {
            lock.unlock();
        }
    }

}
