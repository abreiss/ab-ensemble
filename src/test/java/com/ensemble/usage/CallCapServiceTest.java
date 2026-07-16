package com.ensemble.usage;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallCapServiceTest {

	@Mock
	UsageRepository repository;

	private CallCapService service(int dailyLimit, Instant now) {
		return new CallCapService(new UsageProperties(dailyLimit), repository, Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void underLimit_allowsAndIncrements() {
		when(repository.increment(anyString())).thenReturn(5L);

		service(100, Instant.parse("2026-07-16T10:15:30Z")).reserve();

		verify(repository).increment("2026-07-16");
	}

	@Test
	void atLimit_blocksWith429Signal() {
		when(repository.increment(anyString())).thenReturn(3L);

		assertThatExceptionOfType(DailyCapExceededException.class)
			.isThrownBy(() -> service(2, Instant.parse("2026-07-16T10:15:30Z")).reserve());
	}

	@Test
	void usesUtcDateKey() {
		when(repository.increment(anyString())).thenReturn(1L);

		service(100, Instant.parse("2026-07-16T23:59:59Z")).reserve();

		verify(repository).increment(eq("2026-07-16"));
	}

	@Test
	void newUtcDay_resetsCount() {
		when(repository.increment(anyString())).thenReturn(1L);

		service(100, Instant.parse("2026-07-16T23:59:59Z")).reserve();
		service(100, Instant.parse("2026-07-17T00:00:01Z")).reserve();

		verify(repository).increment("2026-07-16");
		verify(repository).increment("2026-07-17");
	}
}
