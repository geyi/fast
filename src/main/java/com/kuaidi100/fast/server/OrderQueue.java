package com.kuaidi100.fast.server;

import java.util.List;

public class OrderQueue {
    public static final String[] items = new String[1000];
    public static final TimeoutBlockingQueue<String> DATA_QUEUE = new TimeoutBlockingQueue<>(items, 100, 1000);

    public static void put(String s) throws InterruptedException {
        DATA_QUEUE.put(s);
    }

    public static List<String> take() throws InterruptedException {
        return DATA_QUEUE.take();
    }

    public static List<String> poll() throws InterruptedException {
        return DATA_QUEUE.poll();
    }

    public static int size() {
        return DATA_QUEUE.getCount();
    }
}
