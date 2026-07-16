package com.ensemble.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Daily call-cap settings. Bound from {@code ensemble.usage.*}.
 *
 * @param dailyLimit the global daily limit on Claude-backed calls (POST /api/style,
 *     POST /api/items/tag) before further calls are rejected with 429. Defaults to
 *     {@value #DEFAULT_DAILY_LIMIT} when unset.
 */
@ConfigurationProperties(prefix = "ensemble.usage")
public record UsageProperties(int dailyLimit) {

	public static final int DEFAULT_DAILY_LIMIT = 100;

	public UsageProperties {
		if (dailyLimit <= 0) {
			dailyLimit = DEFAULT_DAILY_LIMIT;
		}
	}
}
