package com.advertisingmarketplace.common.money;

public interface ExchangeRateProvider {

    ExchangeRates fetchLatest();
}
