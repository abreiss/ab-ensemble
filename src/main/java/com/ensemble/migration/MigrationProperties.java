package com.ensemble.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One-time data-migration switches bound from {@code ensemble.migration.*} (spec #15).
 *
 * <p>{@code purgeUnowned} gates {@link UnownedDataPurgeRunner}: a one-shot, opt-in cleanup
 * of legacy rows written before per-user ownership existed. It defaults to {@code false}
 * (a boolean component binds to {@code false} when the property is absent), so the runner
 * is a no-op on every ordinary startup — including every {@code @SpringBootTest} context,
 * which has no live DynamoDB. An operator flips it to {@code true} for a single deploy to
 * clear pre-existing unowned data, then flips it back.
 *
 * @param purgeUnowned whether the startup purge of unowned items/outfits is enabled.
 */
@ConfigurationProperties(prefix = "ensemble.migration")
public record MigrationProperties(boolean purgeUnowned) {
}
