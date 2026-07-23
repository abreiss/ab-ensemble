package com.ensemble.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ensemble.config.SeedProperties;

/**
 * Unit tests for {@link SeedAccountRunner} (issue #14). Plain Mockito — no Spring
 * context — proving the three decision branches: seed-and-create, skip-when-present
 * (idempotent), and no-op-when-unconfigured (the critical path that keeps every
 * existing {@code @SpringBootTest} context safe with no seed config and no live
 * DynamoDB).
 */
@ExtendWith(MockitoExtension.class)
class SeedAccountRunnerTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

	@Mock
	UserRepository userRepository;

	@Mock
	PasswordHasher passwordHasher;

	@Test
	void seedsWhenAbsentAndConfigured() throws Exception {
		SeedProperties props = new SeedProperties("Seed@Example.com", "seed-password");
		when(userRepository.findByEmail("Seed@Example.com")).thenReturn(Optional.empty());
		when(passwordHasher.hash("seed-password")).thenReturn("hashed-value");
		SeedAccountRunner runner = new SeedAccountRunner(userRepository, passwordHasher, props, FIXED_CLOCK);

		runner.run(null);

		verify(passwordHasher).hash("seed-password");
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).create(captor.capture());
		User created = captor.getValue();
		assertThat(created.getEmail()).isEqualTo("seed@example.com");
		assertThat(created.getUserId()).isNotBlank();
		assertThat(created.getPasswordHash()).isEqualTo("hashed-value");
		// The raw password must never reach the persisted record — only the hash.
		assertThat(created.getPasswordHash()).isNotEqualTo("seed-password");
		assertThat(created.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Test
	void skipsWhenAccountExists() throws Exception {
		SeedProperties props = new SeedProperties("seed@example.com", "seed-password");
		when(userRepository.findByEmail("seed@example.com")).thenReturn(Optional.of(new User()));
		SeedAccountRunner runner = new SeedAccountRunner(userRepository, passwordHasher, props, FIXED_CLOCK);

		runner.run(null);

		verify(userRepository, never()).create(any());
		verify(passwordHasher, never()).hash(anyString());
	}

	@Test
	void noOpWhenUnconfigured() throws Exception {
		SeedProperties bothBlank = new SeedProperties("", "");
		SeedProperties halfConfigured = new SeedProperties("seed@example.com", "");
		SeedAccountRunner bothBlankRunner =
			new SeedAccountRunner(userRepository, passwordHasher, bothBlank, FIXED_CLOCK);
		SeedAccountRunner halfConfiguredRunner =
			new SeedAccountRunner(userRepository, passwordHasher, halfConfigured, FIXED_CLOCK);

		bothBlankRunner.run(null);
		halfConfiguredRunner.run(null);

		// Neither the fully-blank nor the half-configured seed touches a collaborator —
		// a half-configured seed is treated as unconfigured, not a partial attempt.
		verifyNoInteractions(userRepository, passwordHasher);
	}
}
