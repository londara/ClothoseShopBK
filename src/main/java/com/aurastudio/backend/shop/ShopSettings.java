package com.aurastudio.backend.shop;

/** {@code shop_settings} row in API (camelCase) form. */
public record ShopSettings(String shopName, String telegram, String announcement, String heroTitle,
		String heroSubtitle, String heroImage, String backgroundColor, String aboutTitle, String aboutMe) {

	/** Default hero photo for new shops (same as the Next.js app). */
	public static final String DEFAULT_HERO = "https://lh3.googleusercontent.com/aida-public/AB6AXuBNYkA4rqezuaikey7yzPjQ69kx_QQHyv11hJbq2Ddz8bbymo1rIh57ipCuu1-U15ZzpobDwkn7MqY5dKC1SJR14BoGL3FKOzljbi_hAwtEk89eKtwiCvQkR1Ca6YMvvIdl7pE2s4D_JcZG9afXzVIXri7XB096uq2VvKLYhORqMRzY_0N9EDexcJnPqn1AHUHMv7ZHEQvlew-62AofCTv7PcWEbMXcUzFnSlQADbW7ZyNfB5E77d1Zjg";
}
