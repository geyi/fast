package com.kuaidi100.fast.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

//@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    @PostConstruct
    public void init() {
        new Thread(() -> {
            List<String> list;
            while (true) {
                try {
                    list = OrderQueue.take();
                    log.debug("data list:{}", list.size());
                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    for (String dto : list) {
                    }

                    int length;
                    if ((length = OrderQueue.size()) > 8000) {
                        log.warn("OrderQueue size is {}", length);
                    }
                } catch (Exception e) {
                    log.error("execute queue task|ERROR", e);
                }
            }
        });
    }
}
