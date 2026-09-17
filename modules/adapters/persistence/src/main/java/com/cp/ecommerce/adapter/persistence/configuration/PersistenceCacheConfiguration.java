package com.cp.ecommerce.adapter.persistence.configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.cache.CacheManager;
import javax.cache.Caching;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;

import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration class for caching.
 *
 * <p>
 * The cache backend is selected via the {@code cache.provider} property: {@code ehcache} (default) keeps a local, heap-based
 * cache per instance, while {@code redis} uses a distributed cache shared by every instance. A single-instance deployment works
 * fine with the default, but a horizontally scaled (multi-replica) deployment needs {@code redis} so that all instances observe
 * the same cached order data - Ehcache cannot provide that on its own since each instance's heap cache is independent. Caching
 * can also be disabled entirely via {@code cache.enabled=false}, regardless of the configured provider.
 */
@Configuration
@EnableCaching
public class PersistenceCacheConfiguration {

    @Bean
    @ConditionalOnProperty(name = "cache.enabled", havingValue = "false")
    public org.springframework.cache.CacheManager noOpCacheManager() {

        return new NoOpCacheManager();
    }

    /**
     * Default cache provider: a local, per-instance cache backed by Ehcache. Requires no external dependency, but does not
     * share cache entries across instances - only suitable for a single-instance deployment.
     */
    @Configuration
    @ConditionalOnProperty(name = "cache.enabled", havingValue = "true")
    public static class EhcacheCacheManagerConfiguration {

        @Bean
        @ConditionalOnProperty(name = "cache.provider", havingValue = "ehcache", matchIfMissing = true)
        public org.springframework.cache.CacheManager cacheManager() {

            return new JCacheCacheManager(getCacheManager());
        }

        private CacheManager getCacheManager() {

            final CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
            createCaches(cacheManager, createCacheConfigurations());
            return cacheManager;
        }

        private Map<String, CacheConfiguration<?, ?>> createCacheConfigurations() {

            final Map<String, CacheConfiguration<?, ?>> cacheConfigurations = new HashMap<>();
            Arrays.stream(CacheProperties.values())
                    .forEach(
                            cache -> cacheConfigurations.put(
                                    cache.getCacheName(),
                                    CacheConfigurationBuilder
                                            .newCacheConfigurationBuilder(
                                                    cache.getKeyType(),
                                                    cache.getValueType(),
                                                    ResourcePoolsBuilder.heap(cache.getMaxEntries()))
                                            .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(cache.getDuration()))
                                            .build()));
            return cacheConfigurations;
        }

        private void createCaches(
                final CacheManager cacheManager,
                final Map<String, CacheConfiguration<?, ?>> cacheConfigurations) {

            cacheConfigurations.forEach((name, configuration) -> {
                if (cacheManager.getCache(name) == null) {
                    cacheManager.createCache(name, Eh107Configuration.fromEhcacheCacheConfiguration(configuration));
                }
            });
        }

    }

    /**
     * Distributed cache provider backed by Redis, shared across every application instance. Opt in via
     * {@code cache.provider=redis} (see the {@code cache-redis} Spring profiles) once a reachable Redis instance is available -
     * required for horizontally scaled deployments, see {@link EhcacheCacheManagerConfiguration}.
     *
     * <p>
     * Each cache's values are serialized as JSON, bound to that cache's configured value type ({@link OrderEntity} is a JPA
     * entity, not {@link java.io.Serializable}, so JDK serialization is not an option). Ehcache's per-cache {@code maxEntries}
     * bound has no direct Redis equivalent and is intentionally not applied here; capacity is instead managed at the Redis
     * server/infrastructure level (e.g. {@code
     * maxmemory} and an eviction policy).
     */
    @Configuration
    @ConditionalOnProperty(name = "cache.enabled", havingValue = "true")
    public static class RedisCacheManagerConfiguration {

        @Bean
        @ConditionalOnProperty(name = "cache.provider", havingValue = "redis")
        public org.springframework.cache.CacheManager cacheManager(final RedisConnectionFactory redisConnectionFactory) {

            return RedisCacheManager.builder(redisConnectionFactory)
                    .cacheDefaults(baseCacheConfiguration())
                    .withInitialCacheConfigurations(createCacheConfigurations())
                    .build();
        }

        private RedisCacheConfiguration baseCacheConfiguration() {

            return RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));
        }

        // Jackson2JsonRedisSerializer is deprecated for removal in favor of the Jackson-3-based
        // JacksonJsonRedisSerializer, but this project still depends on Jackson 2 (com.fasterxml.jackson)
        // throughout, so migrating just this serializer to Jackson 3 would add an incompatible, second Jackson
        // major version to the classpath. Bound to each cache's own value type, it also avoids the default
        // polymorphic typing security concern of its untyped sibling, GenericJackson2JsonRedisSerializer.
        @SuppressWarnings("removal")
        private Map<String, RedisCacheConfiguration> createCacheConfigurations() {

            final Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
            Arrays.stream(CacheProperties.values())
                    .forEach(
                            cache -> cacheConfigurations.put(
                                    cache.getCacheName(),
                                    baseCacheConfiguration().entryTtl(cache.getDuration())
                                            .serializeValuesWith(
                                                    RedisSerializationContext.SerializationPair.fromSerializer(
                                                            new Jackson2JsonRedisSerializer<>(cache.getValueType())))));
            return cacheConfigurations;
        }

    }

}
