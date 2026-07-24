package com.ensemble.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ensemble.config.SeedProperties;

/**
 * Idempotently seeds a single default account on startup from
 * {@code ensemble.seed.username} / {@code ensemble.seed.password} (issue #14), bypassing
 * the signup passcode gate server-side — there is no HTTP round trip, so
 * {@code SignupPasscodeVerifier} is never involved.
 *
 * <p>A no-op unless <em>both</em> values are configured ({@link SeedProperties#configured()});
 * a half-configured seed (one set, one blank) is deliberately treated the same as fully
 * unconfigured. This no-op path is critical: every existing {@code @SpringBootTest} context
 * runs this runner with no seed config and no live DynamoDB, so it must return before touching
 * either collaborator. When configured, creation is skipped if the (normalized) username already
 * has an account — safe to run on every startup. The raw password is hashed before storage and
 * never logged; success/skip messages omit it entirely.
 *
 * <p>Ordered to run <em>after</em> {@link com.ensemble.config.DynamoDbTableInitializer}, which
 * creates the users table in local dev, but still gated on nothing else — it also runs under the
 * cloud profile (where table auto-create is off, since the table is Terraform-owned there).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class SeedAccountRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedAccountRunner.class);

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final SeedProperties props;
	private final Clock clock;

	public SeedAccountRunner(UserRepository userRepository, PasswordHasher passwordHasher,
			SeedProperties props, Clock clock) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.props = props;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!props.configured()) {
			log.debug("Seed account not configured (ensemble.seed.username/password unset); skipping");
			return;
		}
		if (userRepository.findByUsername(props.username()).isPresent()) {
			log.info("Seed account already exists; skipping");
			return;
		}
		User user = new User();
		user.setUserId(UUID.randomUUID().toString());
		user.setUsername(props.username());
		user.setPasswordHash(passwordHasher.hash(props.password()));
		user.setCreatedAt(Instant.now(clock));
		userRepository.create(user);
		log.info("Seed account created");
	}
}
