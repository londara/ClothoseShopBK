package com.aurastudio.backend.shoptype;

import java.util.List;
import java.util.Locale;

/**
 * {@code size_kind} enum and the size systems behind it (fixed in code, like
 * {@code SIZE_SETS} in the frontend's src/lib/shop-types.ts).
 */
public enum SizeKind {
	NONE(List.of(), List.of()),
	CLOTHING(List.of(new SizeOption("XS", "ទំហំ ៣៤–៣៦"), new SizeOption("S", "ទំហំ ៣៨–៤០"),
			new SizeOption("M", "ទំហំ ៤២–៤៤"), new SizeOption("L", "ទំហំ ៤៦–៤៨"), new SizeOption("XL", "ទំហំ ៥០"),
			new SizeOption("XXL", "ទំហំ ៥២–៥៤")), List.of("S", "M", "L", "XL")),
	SHOES(List.of("36", "37", "38", "39", "40", "41", "42", "43", "44", "45").stream()
			.map(v -> new SizeOption(v, null)).toList(), List.of("37", "38", "39", "40", "41", "42")),
	KIDS(List.of(new SizeOption("0-1", "អាយុ ០–១ ឆ្នាំ"), new SizeOption("1-2", "អាយុ ១–២ ឆ្នាំ"),
			new SizeOption("3-4", "អាយុ ៣–៤ ឆ្នាំ"), new SizeOption("5-6", "អាយុ ៥–៦ ឆ្នាំ"),
			new SizeOption("7-8", "អាយុ ៧–៨ ឆ្នាំ"), new SizeOption("9-10", "អាយុ ៩–១០ ឆ្នាំ"),
			new SizeOption("11-12", "អាយុ ១១–១២ ឆ្នាំ")), List.of("1-2", "3-4", "5-6", "7-8"));

	private final List<SizeOption> options;
	private final List<String> defaults;

	SizeKind(List<SizeOption> options, List<String> defaults) {
		this.options = options;
		this.defaults = defaults;
	}

	public List<SizeOption> options() {
		return options;
	}

	/** Pre-selected for new products; also assumed for products saved without sizes. */
	public List<String> defaults() {
		return defaults;
	}

	public boolean hasSizes() {
		return !options.isEmpty();
	}

	/** Keep only allowed sizes, in this system's display order. */
	public List<String> ordered(List<String> sizes) {
		return options.stream().map(SizeOption::value).filter(sizes::contains).toList();
	}

	public String db() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static SizeKind fromDb(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

	public record SizeOption(String value, String hint) {
	}
}
