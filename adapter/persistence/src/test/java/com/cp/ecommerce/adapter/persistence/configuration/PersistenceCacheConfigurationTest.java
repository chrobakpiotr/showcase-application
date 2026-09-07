package com.cp.ecommerce.adapter.persistence.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PersistenceCacheConfiguration}.
 */
@ExtendWith(MockitoExtension.class)
class PersistenceCacheConfigurationTest {

    @Mock
    private transient RedisConnectionFactory redisConnectionFactory;

    @Test
    void shouldCreateNoOpCacheManager() {

        final CacheManager cacheManager = new PersistenceCacheConfiguration().noOpCacheManager();

        assertThat(cacheManager).isInstanceOf(org.springframework.cache.support.NoOpCacheManager.class);
    }

    @Test
    void shouldCreateEhcacheCacheManager() {

        final CacheManager cacheManager = new PersistenceCacheConfiguration.EhcacheCacheManagerConfiguration().cacheManager();

        assertThat(cacheManager).isInstanceOf(org.springframework.cache.jcache.JCacheCacheManager.class);
        assertThat(cacheManager.getCache(CacheProperties.ORDER_CACHE.getCacheName())).isNotNull();
    }

    @Test
    void shouldCreateRedisCacheManager() {

        final CacheManager cacheManager = new PersistenceCacheConfiguration.RedisCacheManagerConfiguration()
                .cacheManager(redisConnectionFactory);

        assertThat(cacheManager).isInstanceOf(org.springframework.data.redis.cache.RedisCacheManager.class);
    }

}
