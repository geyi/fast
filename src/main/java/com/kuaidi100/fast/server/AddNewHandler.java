package com.kuaidi100.fast.server;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class AddNewHandler extends ActionHandler {


    private static final AtomicLong AUTO_ORDER_ID = new AtomicLong(0);

    @Override
    public byte[] doHandler(Map<String, String> paramMap) {
        return new byte[0];
    }
}
