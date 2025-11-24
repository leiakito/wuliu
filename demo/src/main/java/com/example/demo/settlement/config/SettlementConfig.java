package com.example.demo.settlement.config;

import com.example.demo.config.AppProperties;
import com.example.demo.hardware.mapper.HardwarePriceMapper;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.settlement.mapper.SettlementRecordMapper;
import com.example.demo.settlement.service.SettlementService;
import com.example.demo.settlement.service.impl.SettlementServiceImpl;
import com.example.demo.submission.mapper.UserSubmissionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SettlementConfig {

    @Bean
    @ConditionalOnMissingBean(SettlementService.class)
    public SettlementService settlementService(SettlementRecordMapper settlementRecordMapper,
                                               OrderRecordMapper orderRecordMapper,
                                               HardwarePriceMapper hardwarePriceMapper,
                                               UserSubmissionMapper userSubmissionMapper,
                                               AppProperties appProperties) {
        return new SettlementServiceImpl(settlementRecordMapper, orderRecordMapper, hardwarePriceMapper, userSubmissionMapper, appProperties);
    }
}
