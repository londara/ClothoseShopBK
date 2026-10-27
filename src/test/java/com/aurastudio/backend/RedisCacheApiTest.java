package com.aurastudio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import com.aurastudio.backend.support.CacheBehaviourTest;

/**
 * Same cache behaviour on Redis (the shared cache for many instances), plus checks that
 * values really live in Redis as JSON and come back intact. Uses Redis database 15 on
 * localhost:6379 and is skipped when no Redis server is running.
 */
@TestPropertySource(properties = { "aura.cache.type=redis", "spring.data.redis.database=15" })
class RedisCacheApiTest extends CacheBehaviourTest {

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private CacheManager cacheManager;

	@BeforeAll
	static void requireRedis() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("localhost", 6379), 500);
		}
		catch (Exception e) {
			Assumptions.abort("No Redis on localhost:6379 — skipping Redis cache tests");
		}
	}

	@AfterEach
	void cleanRedis() {
		var keys = redis.keys("aura:*");
		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
	}

	@Test
	void valuesAreStoredInRedisWithATtlAndReadBackIntact() throws Exception {
		assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
		TestShop shop = activeShop("couture");
		var withColor = new java.util.HashMap<>(product("Silk scarf", List.of("accessories"), List.of("M")));
		withColor.put("colors", List.of(java.util.Map.of("name", "ខ្មៅ", "hex", "#111111")));
		withColor.put("price", 12.5);
		createProduct(shop.token(), withColor);

		String url = "/api/v1/shops/" + shop.slug() + "/products";
		String first = getJson(url, null).andReturn().getResponse().getContentAsString();
		assertThat(redis.hasKey("aura:shop-products::" + shop.shopId())).isTrue();
		assertThat(redis.hasKey("aura:storefront-shop::" + shop.slug())).isTrue();
		assertThat(redis.getExpire("aura:shop-products::" + shop.shopId())).isPositive();

		// Second read is deserialized from Redis: same JSON (prices, sizes, colours, Khmer text).
		String second = getJson(url, null).andReturn().getResponse().getContentAsString();
		assertThat(second).isEqualTo(first).contains("12.5").contains("#111111").contains("ខ្មៅ");

		// The per-request account check is cached too.
		getJson("/api/v1/admin/products", shop.token());
		assertThat(redis.keys("aura:shop-context::*")).isNotEmpty();
	}
}
