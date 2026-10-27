package com.aurastudio.backend.cache;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.aurastudio.backend.config.AuraProperties;
import com.aurastudio.backend.product.ProductDtos.ProductDto;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopCard;
import com.aurastudio.backend.storefront.StorefrontDtos.ShopTypeDto;
import com.aurastudio.backend.storefront.StorefrontDtos.StorefrontShop;
import com.aurastudio.backend.tenant.TenantLookup.TenantUser;
import com.github.benmanes.caffeine.cache.Caffeine;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * Read caches (docs/features/19-caching.md).
 * <ul>
 * <li>{@code aura.cache.type=caffeine} (default): in memory, per instance.</li>
 * <li>{@code aura.cache.type=redis}: shared by every API instance, so an eviction on one
 * instance is seen by all. Use this when running more than one instance.</li>
 * <li>{@code aura.cache.type=none}: no caching (debugging).</li>
 * </ul>
 * Both managers are transaction-aware: evictions made inside a transaction happen after
 * the commit, so a concurrent request can't re-cache the old data in between.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

	/** One cache: lifetime, Caffeine size limit, and its value type (Redis stores JSON). */
	record Spec(String name, Duration ttl, long maxEntries, Function<TypeFactory, JavaType> type) {
	}

	static List<Spec> specs(AuraProperties.Cache p) {
		return List.of(
				new Spec(CacheNames.SHOP_TYPES, p.shopTypesTtl(), 1,
						t -> t.constructCollectionType(List.class, ShopTypeDto.class)),
				new Spec(CacheNames.ACTIVE_SHOPS, p.storefrontTtl(), 1,
						t -> t.constructCollectionType(List.class, ShopCard.class)),
				new Spec(CacheNames.SHOP, p.storefrontTtl(), 50_000, t -> t.constructType(StorefrontShop.class)),
				new Spec(CacheNames.SHOP_PRODUCTS, p.storefrontTtl(), 10_000,
						t -> t.constructCollectionType(List.class, ProductDto.class)),
				new Spec(CacheNames.SHOP_CONTEXT, p.sessionTtl(), 200_000, t -> t.constructType(TenantUser.class)));
	}

	@Bean
	CacheManager cacheManager(AuraProperties properties, ObjectProvider<RedisConnectionFactory> redis,
			JsonMapper json) {
		AuraProperties.Cache p = properties.cache();
		String type = p.type() == null ? "caffeine" : p.type().trim().toLowerCase();
		log.info("Read cache: {}", type);
		return switch (type) {
			case "none" -> new NoOpCacheManager();
			case "redis" -> redis(redis.getObject(), json, p);
			case "caffeine" -> caffeine(p);
			default -> throw new IllegalStateException("aura.cache.type must be caffeine, redis or none: " + type);
		};
	}

	private static CacheManager caffeine(AuraProperties.Cache p) {
		var manager = new CaffeineCacheManager();
		manager.setAllowNullValues(false);
		for (Spec spec : specs(p)) {
			manager.registerCustomCache(spec.name(),
					Caffeine.newBuilder().expireAfterWrite(spec.ttl()).maximumSize(spec.maxEntries()).recordStats()
							.build());
		}
		return new TransactionAwareCacheManagerProxy(manager);
	}

	private static CacheManager redis(RedisConnectionFactory factory, JsonMapper json, AuraProperties.Cache p) {
		var defaults = RedisCacheConfiguration.defaultCacheConfig()
				.disableCachingNullValues()
				.computePrefixWith(name -> "aura:" + name + "::");
		// immediateWrites: evictions are synchronous plain DELs. The default (async UNLINK)
		// silently drops failed evictions, which would leave stale data cached.
		// SCAN instead of KEYS for clear(): KEYS blocks the whole Redis server on big keyspaces.
		var writer = RedisCacheWriter.create(factory,
				config -> config.immediateWrites().batchStrategy(BatchStrategies.scan(1000)).collectStatistics());
		var builder = RedisCacheManager.builder(writer).cacheDefaults(defaults).transactionAware();
		for (Spec spec : specs(p)) {
			var serializer = new JacksonJsonRedisSerializer<Object>(json, spec.type().apply(json.getTypeFactory()));
			builder.withCacheConfiguration(spec.name(),
					defaults.entryTtl(spec.ttl()).serializeValuesWith(SerializationPair.fromSerializer(serializer)));
		}
		return builder.build();
	}
}
