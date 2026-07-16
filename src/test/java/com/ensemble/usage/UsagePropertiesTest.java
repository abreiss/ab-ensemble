package com.ensemble.usage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsagePropertiesTest {

	@Test
	void configuredDailyLimit_isKept() {
		assertThat(new UsageProperties(250).dailyLimit()).isEqualTo(250);
	}

	@Test
	void nonPositiveDailyLimit_fallsBackToDefault() {
		assertThat(new UsageProperties(0).dailyLimit()).isEqualTo(UsageProperties.DEFAULT_DAILY_LIMIT);
	}
}
