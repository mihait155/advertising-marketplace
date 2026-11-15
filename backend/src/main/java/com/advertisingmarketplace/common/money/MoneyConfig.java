package com.advertisingmarketplace.common.money;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CurrencyProperties.class, ExchangeRateProperties.class})
public class MoneyConfig {
}
