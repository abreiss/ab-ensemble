package com.ensemble.usage;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * Enforces the global daily call cap on the Claude-backed endpoints. {@link #reserve()}
 * must be called before the Claude call: it increments the shared counter for the
 * current UTC day first, so a failed/timed-out upstream call still consumes budget,
 * then throws once the increment pushes the count past the configured limit.
 */
@Service
public class CallCapService {

	private final UsageProperties properties;
	private final UsageRepository repository;
	private final Clock clock;

	public CallCapService(UsageProperties properties, UsageRepository repository, Clock clock) {
		this.properties = properties;
		this.repository = repository;
		this.clock = clock;
	}

	/** @throws DailyCapExceededException once today's incremented count exceeds the daily limit */
	public void reserve() {
		String utcDate = LocalDate.now(clock).toString();
		long count = repository.increment(utcDate);
		if (count > properties.dailyLimit()) {
			throw new DailyCapExceededException("daily call limit reached, try again tomorrow");
		}
	}
}
