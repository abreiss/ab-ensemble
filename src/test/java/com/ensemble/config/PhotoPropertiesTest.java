package com.ensemble.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhotoPropertiesTest {

	@Test
	void configuredPixelCap_isKept() {
		assertThat(new PhotoProperties("./photos", 1_234L).maxUploadPixels()).isEqualTo(1_234L);
	}

	@Test
	void nonPositivePixelCap_fallsBackToDefault() {
		assertThat(new PhotoProperties("./photos", 0L).maxUploadPixels())
			.isEqualTo(PhotoProperties.DEFAULT_MAX_UPLOAD_PIXELS);
	}
}
