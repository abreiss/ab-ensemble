package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Pins the {@code cloud} profile overlay ({@code application-cloud.yml}) that
 * App Runner activates via {@code SPRING_PROFILES_ACTIVE=cloud}
 * (terraform/deploy/apprunner.tf). The overlay must:
 *
 * <ul>
 *   <li>blank the DynamoDB endpoint, so {@link DynamoDbConfig} takes the real-AWS
 *       branch (default credential chain, no {@code localhost:8000} override).
 *       App Runner silently drops empty-string runtime environment variables,
 *       so {@code ENSEMBLE_DYNAMODB_ENDPOINT=""} never reaches the container —
 *       the override has to live in this profile file, not the environment
 *       (learned the hard way in issue #9 task 6.1: the container crashed in
 *       the cloud dialing DynamoDB Local);</li>
 *   <li>disable table auto-create, because the cloud table is Terraform-owned
 *       and the App Runner instance role deliberately has item-level DynamoDB
 *       permissions only — {@link DynamoDbTableInitializer}'s startup
 *       {@code DescribeTable} would be an {@code AccessDeniedException} crash.</li>
 * </ul>
 */
class CloudProfileConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withUserConfiguration(DynamoDbConfig.class, DynamoDbTableInitializer.class);

	@Test
	void cloudProfile_blanksDynamoDbEndpoint_soRealAwsBranchIsTaken() {
		runner.withPropertyValues("spring.profiles.active=cloud").run(context -> {
			DynamoDbProperties props = context.getBean(DynamoDbProperties.class);
			assertThat(props.endpoint()).as("cloud profile must blank the DynamoDB Local endpoint").isBlank();
			assertThat(DynamoDbConfig.isLocalEndpoint(props.endpoint())).isFalse();
		});
	}

	@Test
	void cloudProfile_disablesTableAutoCreate_initializerBeanAbsent() {
		runner.withPropertyValues("spring.profiles.active=cloud").run(context ->
			assertThat(context.getBeansOfType(DynamoDbTableInitializer.class))
				.as("table auto-create must stay off in the cloud (table is Terraform-owned)")
				.isEmpty());
	}

	@Test
	void withoutCloudProfile_localEndpointDefaultIsPreserved() {
		runner.run(context -> {
			DynamoDbProperties props = context.getBean(DynamoDbProperties.class);
			assertThat(props.endpoint()).as("local dev default must stay DynamoDB Local").isNotBlank();
			assertThat(DynamoDbConfig.isLocalEndpoint(props.endpoint())).isTrue();
		});
	}
}
