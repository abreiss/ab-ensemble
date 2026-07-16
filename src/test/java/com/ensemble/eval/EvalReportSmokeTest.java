package com.ensemble.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ensemble.tagging.dto.TagSuggestion;

/**
 * Drives the whole report pipeline in-process with synthetic runs — token summing, the
 * {@link CostCalculator}/{@link JudgeTally} integration, and Markdown/JSON writing — so a
 * formatting or wiring bug surfaces without any live API call. (The live tagging/judge calls
 * themselves are verified by the manual {@code taggingEval} run.)
 */
class EvalReportSmokeTest {

	@TempDir
	Path tmp;

	@Test
	void writesMarkdownAndJson_withSummaryCostAndJudge() throws IOException {
		GoldTag gold = new GoldTag("a.jpg", "top", "black", "white", 2, "graphic", 1, List.of("band-tee"));
		TagSuggestion haikuTags = new TagSuggestion("top", "black", "white", 2, "graphic", 1, List.of("band-tee"));
		TagSuggestion sonnetTags = new TagSuggestion("top", "navy", null, 3, "solid", 2, List.of("crew-neck"));
		ModelRun haiku = new ModelRun("claude-haiku-4-5", "{}", haikuTags, 900, 1200, 150, false);
		ModelRun sonnet = new ModelRun("claude-sonnet-5", "{}", sonnetTags, 1400, 1200, 180, false);

		ImageOutcome outcome = new ImageOutcome("a.jpg", gold, haiku, sonnet,
			AccuracyScorer.score(haikuTags, gold), AccuracyScorer.score(sonnetTags, gold),
			true, "claude-haiku-4-5", "claude-sonnet-5", "A", "Haiku matched the color better",
			1500, 200, 2000);

		Path md = EvalReport.write(tmp, "test", "claude-haiku-4-5", "claude-sonnet-5",
			List.of(outcome), true, LocalDate.of(2026, 7, 15));

		assertThat(md).exists();
		String text = Files.readString(md);
		assertThat(text)
			.contains("# Tagging model eval")
			.contains("Category accuracy")
			.contains("Judge wins")
			.contains("Total $")
			.contains("Human spot-check")
			.contains("a.jpg");
		assertThat(tmp.resolve("results-test.json")).exists();
	}
}
