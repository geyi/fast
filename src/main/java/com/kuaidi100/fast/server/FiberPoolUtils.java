package com.kuaidi100.fast.server;

import co.paralleluniverse.fibers.Fiber;

public class FiberPoolUtils {
    private volatile static FiberPoolUtils instance = null;
    private static final Fiber<Void>[] FIBERS = new Fiber[100000];

    private FiberPoolUtils() {}

    private FiberPoolUtils getInstance() {
        if (instance == null) {
            synchronized (FiberPoolUtils.class) {
                if (instance == null) {
                    instance = new FiberPoolUtils();
                }
            }
        }
        return instance;
    }
}
