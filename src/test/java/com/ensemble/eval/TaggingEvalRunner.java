package com.ensemble.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.ensemble.config.PhotoProperties;
import com.ensemble.eval.AccuracyScorer.TagScore;
import com.ensemble.eval.TagJudge.JudgeRun;
import com.ensemble.storage.ImageProcessor;
import com.ensemble.tagging.dto.TagSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Offline experiment entry point: tags a labeled photo set with Haiku 4.5 and Sonnet 5 through
 * the production request path, scores both against gold, optionally runs the Opus 4.8 judge, and
 * writes a Markdown + JSON report. Run explicitly via {@code ./gradlew taggingEval} — it is never
 * part of the {@code test} task and never ships in the jar, so it can only spend money when a
 * human invokes it.
 *
 * <p>Usage: {@code taggingEval [--images DIR] [--gold FILE] [--out DIR] [--judge]}.
 * Needs {@code ENSEMBLE_ANTHROPIC_API_KEY} (or the SDK's {@code ANTHROPIC_API_KEY}) in the env.
 */
public final class TaggingEvalRunner {

	static final String HAIKU = "claude-haiku-4-5";
	static final String SONNET = "claude-sonnet-5";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final long SEED = 1234L; // fixed so the A/B assignment (and thus the report) is reproducible

	private TaggingEvalRunner() {
	}

	public static void main(String[] args) throws Exception {
		Path imagesDir = Path.of(arg(args, "--images", "eval/tagging/images"));
		Path goldFile = Path.of(arg(args, "--gold", "eval/tagging/gold.json"));
		Path outDir = Path.of(arg(args, "--out", "eval/tagging/out"));
		boolean judge = flag(args, "--judge");
		LocalDate today = LocalDate.now();

		GoldTag[] gold = MAPPER.readValue(Files.readAllBytes(goldFile), GoldTag[].class);
		System.out.printf("Loaded %d gold labels from %s%n", gold.length, goldFile);

		AnthropicClient client = buildClient();
		ImageProcessor imageProcessor = new ImageProcessor(new PhotoProperties(null, 0));
		EvalVisionClient vision = new EvalVisionClient(client);
		TagJudge tagJudge = new TagJudge(client);
		Random rnd = new Random(SEED);

		List<ImageOutcome> outcomes = new ArrayList<>();
		for (GoldTag g : gold) {
			Path imgPath = imagesDir.resolve(g.image());
			if (!Files.isRegularFile(imgPath)) {
				System.out.printf("  ! skipping %s — file not found%n", g.image());
				continue;
			}
			try {
				byte[] resized = imageProcessor.toResizedJpeg(Files.readAllBytes(imgPath));

				ModelRun haiku = vision.run(HAIKU, resized, false);
				ModelRun sonnet = vision.run(SONNET, resized, true);
				TagScore haikuScore = AccuracyScorer.score(haiku.tags(), g);
				TagScore sonnetScore = AccuracyScorer.score(sonnet.tags(), g);

				ImageOutcome outcome;
				if (judge) {
					// Randomize which model is "A" so the judge can't favor a position.
					boolean haikuIsA = rnd.nextBoolean();
					String modelA = haikuIsA ? HAIKU : SONNET;
					String modelB = haikuIsA ? SONNET : HAIKU;
					TagSuggestion setA = haikuIsA ? haiku.tags() : sonnet.tags();
					TagSuggestion setB = haikuIsA ? sonnet.tags() : haiku.tags();
					JudgeRun jr = tagJudge.judge(resized, setA, setB, g);
					outcome = new ImageOutcome(g.image(), g, haiku, sonnet, haikuScore, sonnetScore,
						true, modelA, modelB, jr.verdict().winner(), jr.verdict().reason(),
						jr.inputTokens(), jr.outputTokens(), jr.latencyMs());
				} else {
					outcome = new ImageOutcome(g.image(), g, haiku, sonnet, haikuScore, sonnetScore,
						false, null, null, null, null, 0, 0, 0);
				}
				outcomes.add(outcome);
				System.out.printf("  %s — Haiku %dms, Sonnet %dms%s%n", g.image(),
					haiku.latencyMs(), sonnet.latencyMs(), judge ? " (judged)" : "");
			} catch (RuntimeException e) {
				System.out.printf("  ! %s failed: %s%n", g.image(), e.getMessage());
			}
		}

		if (outcomes.isEmpty()) {
			System.out.println("No images processed — nothing to report.");
			return;
		}

		String runId = today.toString();
		Path report = EvalReport.write(outDir, runId, HAIKU, SONNET, outcomes, judge, today);
		System.out.printf("%nWrote report: %s%n", report.toAbsolutePath());
	}

	private static AnthropicClient buildClient() {
		AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().timeout(Duration.ofSeconds(60));
		String key = System.getenv("ENSEMBLE_ANTHROPIC_API_KEY");
		if (key != null && !key.isBlank()) {
			builder.apiKey(key);
		} else {
			builder.fromEnv(); // fall back to the SDK's standard ANTHROPIC_API_KEY resolution
		}
		return builder.build();
	}

	private static String arg(String[] args, String name, String fallback) {
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equals(name)) {
				return args[i + 1];
			}
		}
		return fallback;
	}

	private static boolean flag(String[] args, String name) {
		for (String a : args) {
			if (a.equals(name)) {
				return true;
			}
		}
		return false;
	}
}
