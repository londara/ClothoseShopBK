package com.aurastudio.backend.common;

/** Khmer numerals for messages ("15" → "១៥"), like {@code kh()} in the frontend. */
public final class Khmer {

	private static final char[] DIGITS = { '០', '១', '២', '៣', '៤', '៥', '៦', '៧', '៨', '៩' };

	private Khmer() {
	}

	public static String digits(long value) {
		String text = Long.toString(value);
		StringBuilder out = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			out.append(Character.isDigit(c) ? DIGITS[c - '0'] : c);
		}
		return out.toString();
	}
}
