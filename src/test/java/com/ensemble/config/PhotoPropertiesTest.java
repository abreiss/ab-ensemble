package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhotoPropertiesTest {

	@Test
	void configuredPixelCap_isKept() {
		assertThat(new PhotoProperties("./photos", 1_234L, "disk", null).maxUploadPixels()).isEqualTo(1_234L);
	}

	@Test
	void nonPositivePixelCap_fallsBackToDefault() {
		assertThat(new PhotoProperties("./photos", 0L, "disk", null).maxUploadPixels())
			.isEqualTo(PhotoProperties.DEFAULT_MAX_UPLOAD_PIXELS);
	}

	@Test
	void blankOrAbsentBackend_fallsBackToDisk() {
		assertThat(new PhotoProperties("./photos", 1_234L, null, null).backend()).isEqualTo("disk");
		assertThat(new PhotoProperties("./photos", 1_234L, "", null).backend()).isEqualTo("disk");
	}

	@Test
	void configuredBackend_isKept() {
		assertThat(new PhotoProperties("./photos", 1_234L, "s3", null).backend()).isEqualTo("s3");
	}

	@Test
	void absentS3Binding_fallsBackToEmptyBucket() {
		assertThat(new PhotoProperties("./photos", 1_234L, "s3", null).s3().bucket()).isNull();
	}

	@Test
	void configuredS3Bucket_isKept() {
		PhotoProperties props =
			new PhotoProperties("./photos", 1_234L, "s3", new PhotoProperties.S3("my-bucket"));

		assertThat(props.s3().bucket()).isEqualTo("my-bucket");
	}
}
