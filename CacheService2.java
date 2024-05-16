package com.digit.payment.service.impl;

import com.digit.payment.entity.CardMaster;
import com.digit.payment.entity.PaymentGatewayUIConfig;
import com.digit.payment.model.gateway.JuspayPaymentMethods;
import com.digit.payment.repository.CardMasterRepository;
import com.digit.payment.repository.UIConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.digit.payment.constants.PaymentConstants.GATEWAYS_CARD_DETAILS_CACHE;
import static com.digit.payment.constants.PaymentConstants.JUSPAY_PAYMENT_METHODS_CACHE;
import static com.digit.payment.constants.PaymentConstants.UI_CONFIG_CACHE;

/**
 * @Author : Saravana.Kumar01
 * @CreatedAt : 30 October 2022
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    // Dependency Injection
    private final CacheManager cacheManager;
    private final JuspayGateway juspayGateway;
    private final UIConfigRepository uiConfigRepository;
    private final CardMasterRepository cardMasterRepository;

    /* ---------------------- Cacheable Data Methods ----------------------*/
    @Cacheable(value = JUSPAY_PAYMENT_METHODS_CACHE, key = "#companyCode.toString()", sync = true)
    public JuspayPaymentMethods getCachedPaymentMethods(Character companyCode) {
        return juspayGateway.getPaymentMethodsFromGateway(companyCode);
    }

    @Cacheable(value = UI_CONFIG_CACHE, key = "#gatewayId", sync = true)
    public Map<String, String> getCachedUIConfigValues(String gatewayId) {
        List<PaymentGatewayUIConfig> uiConfigs = uiConfigRepository.findAllByGatewayId(gatewayId);
        return uiConfigs.stream().collect(
                Collectors.toMap(PaymentGatewayUIConfig::getConfigKey, PaymentGatewayUIConfig::getConfigValue)
        );
    }

    @Cacheable(value = GATEWAYS_CARD_DETAILS_CACHE, key = "#cardId" ,unless = "#result == null")
    public CardMaster getCachedCardBinDetails(Integer cardId) {
    	return cardMasterRepository.findTop1ByCardIdOrderByCreatedTimeDesc(cardId);
    }

    /* ---------------------- Evict Cache Methods ----------------------*/

    public void evictAllCaches() {
        cacheManager.getCacheNames().forEach(this::evictCacheByName);
    }

    /**
     * @param cacheName valid Cache Name
     * @summary - Clears specific Cache
     */
    public void evictCacheByName(String cacheName) {
        if (log.isInfoEnabled()) {
            log.info("Clearing cache for ---Cache Name : " + cacheName);
        }
        Optional.ofNullable(cacheManager.getCache(cacheName)).ifPresent(Cache::clear);
    }

    /**
     * @param cacheName     valid Cache Name
     * @param cacheEntryKey valid Cache Entry Key
     * @return A {@link String} having response message
     * @summary - Removes specific entry(Key) in a Cache.
     */
    public String evictCacheEntryByKey(String cacheName, String cacheEntryKey) {
        Cache cache = cacheManager.getCache(cacheName);

        //Validating the cache Object for 'null'.
        if (ObjectUtils.isEmpty(ObjectUtils.isEmpty(cache) ? null : cache.getNativeCache())) {
            return "Key: '" + cacheEntryKey + "' is not present in Cache: '" + cacheName + "' as the Cache is Empty.";
        }

        boolean isCacheEntryEvicted = cache.evictIfPresent(cacheEntryKey);
        return isCacheEntryEvicted
                ? "Successfully cleared '" + cacheEntryKey + "' entry in '" + cacheName + "'."
                : "Key: '" + cacheEntryKey + "' is not present in '" + cacheName + "'";
    }

    @Scheduled(fixedRate = 25, timeUnit = TimeUnit.MINUTES)
    @CacheEvict(value = JUSPAY_PAYMENT_METHODS_CACHE, allEntries = true)
    public void clearPaymentMethodCache() {
        if (log.isInfoEnabled()) {
            log.info("Clearing cache for ---Cache Name : " + JUSPAY_PAYMENT_METHODS_CACHE);
        }
    }

    @Scheduled(fixedRate = 3, timeUnit = TimeUnit.HOURS)
    @CacheEvict(value = UI_CONFIG_CACHE, allEntries = true)
    public void clearUIConfigCache() {
        if (log.isInfoEnabled()) {
            log.info("Clearing cache for ---Cache Name : " + UI_CONFIG_CACHE);
        }
    }

}