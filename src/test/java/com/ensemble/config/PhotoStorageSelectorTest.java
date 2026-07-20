package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ensemble.storage.ImageProcessor;
import com.ensemble.storage.LocalDiskPhotoStorage;
import com.ensemble.storage.PhotoStorage;
import com.ensemble.storage.S3PhotoStorage;

/**
 * Verifies exactly one {@link PhotoStorage} bean exists for every value of
 * {@code ensemble.photos.backend} — never zero (a broken condition would leave
 * the app with no storage bean at all) and never two (a broken condition would
 * fail context startup with a {@code NoUniqueBeanDefinitionException} the
 * moment any collaborator autowires {@link PhotoStorage}).
 */
class PhotoStorageSelectorTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfig.class)
		.withPropertyValues("ensemble.photos.dir=build/photo-selector-test");

	@Test
	void unsetBackend_registersOnlyLocalDiskPhotoStorage() {
		runner.run(context -> {
			assertThat(context.getBeansOfType(PhotoStorage.class)).hasSize(1);
			assertThat(context.getBean(PhotoStorage.class)).isInstanceOf(LocalDiskPhotoStorage.class);
		});
	}

	@Test
	void diskBackend_registersOnlyLocalDiskPhotoStorage() {
		runner.withPropertyValues("ensemble.photos.backend=disk").run(context -> {
			assertThat(context.getBeansOfType(PhotoStorage.class)).hasSize(1);
			assertThat(context.getBean(PhotoStorage.class)).isInstanceOf(LocalDiskPhotoStorage.class);
		});
	}

	@Test
	void s3Backend_registersOnlyS3PhotoStorage() {
		runner
			.withPropertyValues(
				"ensemble.photos.backend=s3",
				"ensemble.photos.s3.bucket=abreiss-ensemble-photos",
				"ensemble.dynamodb.region=us-east-1")
			.run(context -> {
				assertThat(context.getBeansOfType(PhotoStorage.class)).hasSize(1);
				assertThat(context.getBean(PhotoStorage.class)).isInstanceOf(S3PhotoStorage.class);
			});
	}

	@Configuration
	@Import({ImageProcessor.class, LocalDiskPhotoStorage.class, S3PhotoStorage.class, StorageConfig.class})
	@EnableConfigurationProperties({PhotoProperties.class, DynamoDbProperties.class})
	static class TestConfig {
	}
}
