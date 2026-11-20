package tn.rnu.eniso.doserunner.order.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(FormularyCacheProperties.class)
public class CacheConfig {

    public static final String FORMULARY_CACHE = "formulary";

    @Bean
    public CacheManager cacheManager(FormularyCacheProperties properties) {
        CaffeineCacheManager caffeine = new CaffeineCacheManager(FORMULARY_CACHE);
        caffeine.setCaffeine(Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.timeToLive())
                .recordStats());
        caffeine.setAllowNullValues(false);
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
