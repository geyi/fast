package com.kuaidi100.fast.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 超时阻塞队列
 *
 * @author GEYI
 * @date 2021年04月23日 11:24
 */
public class TimeoutBlockingQueue<T> {

    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private int limit;
    private long timeout;
    private int putptr, takeptr, count;

    private T[] items;

    public TimeoutBlockingQueue(T[] items) {
        this(items, 10, 5000);
    }

    public TimeoutBlockingQueue(T[] items, int limit, long timeout) {
        this.items = items;
        this.limit = limit;
        this.timeout = timeout;
    }

    /**
     * 获取当前队列中元素个数的一个大概值
     *
     * @return int
     * @author GEYI
     * @date 2021年04月28日 14:05
     */
    public int getCount() {
        return count;
    }

    public void put(T x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) {
                notFull.await();
            }
            items[putptr] = x;
            if (++putptr == items.length) {
                putptr = 0;
            }
            ++count;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public List<T> take() throws InterruptedException {
        lock.lock();
        try {
            long future = System.currentTimeMillis() + timeout;
            long remaining = timeout;
            while (count < limit && remaining > 0) {
                notEmpty.await(remaining, TimeUnit.MILLISECONDS);
                remaining = future - System.currentTimeMillis();
            }

            int min = Math.min(count, limit);
            List<T> list = new ArrayList<>(min);
            for (int i = 0; i < min; i++) {
                T item = items[takeptr];
                if (item == null) {
                    break;
                }
                list.add(item);
                if (++takeptr == items.length) {
                    takeptr = 0;
                }
                --count;
            }
            notFull.signal();
            return list;
        } finally {
            lock.unlock();
        }
    }

    public List<T> poll() throws InterruptedException {
        lock.lock();
        try {
            if (count < limit) {
                return null;
            }

            List<T> list = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                T item = items[takeptr];
                if (item == null) {
                    break;
                }
                list.add(item);
                if (++takeptr == items.length) {
                    takeptr = 0;
                }
                --count;
            }
            notFull.signal();
            return list;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        // 测试
        TimeoutBlockingQueue<Object> queue = new TimeoutBlockingQueue<>(new Object[100], 10, 500);

        Thread putThread = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                try {
                    queue.put(new Object());
                    System.out.println("put " + i);
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        Thread putThread2 = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                try {
                    queue.put(new Object());
                    System.out.println("put " + i);
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Thread takeThread = new Thread(() -> {
            try {
                int total = 0;
                for (int i = 0; i < 100; i++) {
                    List<Object> list = queue.poll();
                    if (list == null) {
                        System.out.println("null");
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    }
                    total += list.size();
                    System.out.println("take " + list.size() + ", total " + total);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        putThread.start();
        putThread2.start();
        takeThread.start();
    }

}
