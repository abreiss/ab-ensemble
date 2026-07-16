package com.ensemble.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ensemble.eval.AccuracyScorer.Aggregate;
import com.ensemble.eval.AccuracyScorer.TagScore;
import com.ensemble.eval.JudgeResultParser.JudgeVerdict;
import com.ensemble.eval.JudgeTally.ImageJudgement;
import com.ensemble.eval.JudgeTally.Tally;
import com.ensemble.tagging.dto.TagSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Renders a run's per-image outcomes into a human-readable Markdown report plus a machine-readable
 * {@code results.json}. All aggregation delegates to the unit-tested pure classes
 * ({@link AccuracyScorer}, {@link CostCalculator}, {@link JudgeTally}); this class only formats and
 * writes. Eval harness only.
 */
final class EvalReport {

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final int SPOT_CHECK_SAMPLE = 5;

	private EvalReport() {
	}

	/** Writes {@code report-<runId>.md} and {@code results-<runId>.json}; returns the Markdown path. */
	static Path write(Path outDir, String runId, String haikuModel, String sonnetModel,
			List<ImageOutcome> outcomes, boolean judged, LocalDate date) {
		try {
			Files.createDirectories(outDir);
			Path md = outDir.resolve("report-" + runId + ".md");
			Files.writeString(md, markdown(runId, haikuModel, sonnetModel, outcomes, judged, date));
			Files.writeString(outDir.resolve("results-" + runId + ".json"), MAPPER.writeValueAsString(outcomes));
			return md;
		} catch (IOException e) {
			throw new UncheckedIOException("failed to write eval report", e);
		}
	}

	private static String markdown(String runId, String haikuModel, String sonnetModel,
			List<ImageOutcome> outcomes, boolean judged, LocalDate date) {
		List<TagScore> haikuScores = new ArrayList<>();
		List<TagScore> sonnetScores = new ArrayList<>();
		long hIn = 0, hOut = 0, hLat = 0, sIn = 0, sOut = 0, sLat = 0;
		int hParseErr = 0, sParseErr = 0;
		long jIn = 0, jOut = 0;
		int judgedCount = 0;
		List<ImageJudgement> judgements = new ArrayList<>();
		for (ImageOutcome o : outcomes) {
			haikuScores.add(o.haikuScore());
			sonnetScores.add(o.sonnetScore());
			hIn += o.haiku().inputTokens();
			hOut += o.haiku().outputTokens();
			hLat += o.haiku().latencyMs();
			sIn += o.sonnet().inputTokens();
			sOut += o.sonnet().outputTokens();
			sLat += o.sonnet().latencyMs();
			if (o.haiku().parseError()) {
				hParseErr++;
			}
			if (o.sonnet().parseError()) {
				sParseErr++;
			}
			if (o.judged()) {
				judgedCount++;
				jIn += o.judgeInputTokens();
				jOut += o.judgeOutputTokens();
				judgements.add(new ImageJudgement(o.judgeModelA(), o.judgeModelB(),
					new JudgeVerdict(o.judgeWinner(), java.util.Map.of(), o.judgeReason())));
			}
		}
		int n = outcomes.size();
		Aggregate ha = AccuracyScorer.aggregate(haikuScores);
		Aggregate sa = AccuracyScorer.aggregate(sonnetScores);
		Tally tally = JudgeTally.tally(judgements);

		double hTotal = CostCalculator.costUsd(haikuModel, hIn, hOut, date);
		double sTotal = CostCalculator.costUsd(sonnetModel, sIn, sOut, date);
		double jTotal = judgedCount == 0 ? 0.0 : CostCalculator.costUsd(TagJudge.JUDGE_MODEL, jIn, jOut, date);

		StringBuilder b = new StringBuilder();
		b.append("# Tagging model eval — Haiku 4.5 vs Sonnet 5\n\n");
		b.append("Run `").append(runId).append("` • ").append(n).append(" images • ").append(date).append("\n\n");

		b.append("## Summary\n\n");
		b.append("| Metric | Haiku 4.5 | Sonnet 5 |\n|---|---|---|\n");
		row(b, "Category accuracy", pct(ha.categoryPct()), pct(sa.categoryPct()));
		row(b, "Primary color", pct(ha.primaryColorPct()), pct(sa.primaryColorPct()));
		row(b, "Secondary color", pct(ha.secondaryColorPct()), pct(sa.secondaryColorPct()));
		row(b, "Pattern", pct(ha.patternPct()), pct(sa.patternPct()));
		row(b, "Formality (exact / ±1)", pct(ha.formalityExactPct()) + " / " + pct(ha.formalityWithinOnePct()),
			pct(sa.formalityExactPct()) + " / " + pct(sa.formalityWithinOnePct()));
		row(b, "Warmth (exact / ±1)", pct(ha.warmthExactPct()) + " / " + pct(ha.warmthWithinOnePct()),
			pct(sa.warmthExactPct()) + " / " + pct(sa.warmthWithinOnePct()));
		row(b, "Descriptor F1 (mean)", f2(ha.meanDescriptorF1()), f2(sa.meanDescriptorF1()));
		if (judged) {
			row(b, "Judge wins", String.valueOf(tally.wins().getOrDefault(haikuModel, 0)),
				String.valueOf(tally.wins().getOrDefault(sonnetModel, 0)));
		}
		row(b, "Avg latency (ms)", avg(hLat, n), avg(sLat, n));
		row(b, "Avg $/call", usd(safeDiv(hTotal, n)), usd(safeDiv(sTotal, n)));
		row(b, "Total $", usd(hTotal), usd(sTotal));
		row(b, "Parse errors", String.valueOf(hParseErr), String.valueOf(sParseErr));
		b.append('\n');
		if (judged) {
			b.append("Judge (Opus 4.8): ").append(judgedCount).append(" comparisons, ")
				.append(tally.ties()).append(" ties, judge cost ").append(usd(jTotal)).append(".\n\n");
		}

		b.append("## Per-image\n\n");
		for (ImageOutcome o : outcomes) {
			b.append("### ").append(o.image()).append("\n");
			b.append("- **gold**: ").append(renderGold(o.gold())).append('\n');
			b.append("- **Haiku**: ").append(render(o.haiku().tags())).append("  _(")
				.append(o.haiku().latencyMs()).append("ms, ").append(o.haiku().inputTokens()).append('/')
				.append(o.haiku().outputTokens()).append(" tok)_").append(o.haiku().parseError() ? " ⚠ parse error" : "")
				.append('\n');
			b.append("- **Sonnet**: ").append(render(o.sonnet().tags())).append("  _(")
				.append(o.sonnet().latencyMs()).append("ms, ").append(o.sonnet().inputTokens()).append('/')
				.append(o.sonnet().outputTokens()).append(" tok)_").append(o.sonnet().parseError() ? " ⚠ parse error" : "")
				.append('\n');
			if (o.judged()) {
				String winnerModel = switch (o.judgeWinner()) {
					case "A" -> o.judgeModelA();
					case "B" -> o.judgeModelB();
					default -> "tie";
				};
				b.append("- **Judge**: ").append(winnerModel).append(" — \"").append(o.judgeReason()).append("\"\n");
			}
			b.append('\n');
		}

		b.append("## Human spot-check (fill in)\n\n");
		b.append("Sanity-check the Opus judge against your own eye on a sample:\n\n");
		b.append("| Image | Your pick (H / S / tie) | Notes |\n|---|---|---|\n");
		for (int i = 0; i < Math.min(SPOT_CHECK_SAMPLE, n); i++) {
			b.append("| ").append(outcomes.get(i).image()).append(" |  |  |\n");
		}
		b.append('\n');

		b.append("## Methodology\n\n");
		b.append("- Both models received the **same ≤800px JPEG** (production `ImageProcessor`), tagged via the ")
			.append("**same forced-tool request** (`AnthropicVisionModelClient.tagRequestBuilder`) and parsed via ")
			.append("the **same** `TaggingService.map`.\n");
		b.append("- Sonnet 5 run with **thinking disabled** (Haiku's default); single-shot per image.\n");
		b.append("- Judge saw the two sets **blind** (A/B order randomized per image).\n");
		b.append("- Pricing per 1M tokens on ").append(date).append(": Haiku $1/$5; Sonnet ")
			.append(date.isAfter(CostCalculator.SONNET_INTRO_UNTIL) ? "$3/$15 (sticker)"
				: "$2/$10 (intro, through " + CostCalculator.SONNET_INTRO_UNTIL + ")")
			.append("; Opus judge $5/$25.\n");
		return b.toString();
	}

	private static void row(StringBuilder b, String metric, String haiku, String sonnet) {
		b.append("| ").append(metric).append(" | ").append(haiku).append(" | ").append(sonnet).append(" |\n");
	}

	private static String render(TagSuggestion t) {
		return "cat=" + t.category() + ", primary=" + t.primaryColor() + ", secondary=" + t.secondaryColor()
			+ ", formality=" + t.formality() + ", pattern=" + t.pattern() + ", warmth=" + t.warmth()
			+ ", descriptors=" + t.descriptors();
	}

	private static String renderGold(GoldTag g) {
		return "cat=" + g.category() + ", primary=" + g.primaryColor() + ", secondary=" + g.secondaryColor()
			+ ", formality=" + g.formality() + ", pattern=" + g.pattern() + ", warmth=" + g.warmth()
			+ ", descriptors=" + g.descriptors();
	}

	private static String pct(double v) {
		return String.format("%.1f%%", v);
	}

	private static String f2(double v) {
		return String.format("%.2f", v);
	}

	private static String usd(double v) {
		return String.format("$%.4f", v);
	}

	private static String avg(long total, int n) {
		return n == 0 ? "—" : String.valueOf(total / n);
	}

	private static double safeDiv(double total, int n) {
		return n == 0 ? 0.0 : total / n;
	}
}
