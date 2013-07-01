package org.flib.xdstore.utils;

public final class StringUtils {

	private StringUtils() {
		// do nothing
	}

	public static boolean isBlank(final String str) {
		return str == null || str.trim().length() == 0;
	}
}
