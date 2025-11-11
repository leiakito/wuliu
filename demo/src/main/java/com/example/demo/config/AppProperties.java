package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Settlement settlement = new Settlement();
    private Export export = new Export();

    @Data
    public static class Settlement {
        private boolean warnDoubleBilling = true;
    }

    @Data
    public static class Export {
        private int maxRows = 10000;
    }
}
