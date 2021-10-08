package com.kuaidi100.fast.pi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties({PiConfigProperties.class})
@Component
public class PickedPiReader {
    @Autowired
    private PiConfigProperties properties;


}
