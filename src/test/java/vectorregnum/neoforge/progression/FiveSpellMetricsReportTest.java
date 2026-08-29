package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vectorregnum.core.Element;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.semantic.LoweringContext;
import vectorregnum.core.semantic.SemanticProgram;
import vectorregnum.core.semantic.SemanticVmLowerer;
import vectorregnum.neoforge.CastingConfig;
import vectorregnum.neoforge.LibrarySemanticAdapter;
import vectorregnum.neoforge.effect.PersistentEffectService;

class FiveSpellMetricsReportTest {
    private static final int WARMUP_COMPILES = 2_000;
    private static final int MEASURED_COMPILES = 2_001;

    @Test
    void writesReproducibleMetricsForTheFiveNewSpells() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("id\ttitle\telement\tsemantic_steps\tvm_instructions\tcompile_median_us"
                + "\tmana_100\tmana_75\tmana_50\tmana_25\tbare_cast_ticks\tbare_cast_seconds"
                + "\tquoted_upkeep_100\tcommitted_upkeep_100\teffect_ticks\teffect_seconds"
                + "\tupkeep_interval_ticks\tupkeep_installments\tupkeep_per_interval_100");
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            if (!ProgressionSpellLibrary.FIVE_SPELL_EXPANSION_IDS.contains(spell.id())) continue;
            SemanticProgram semantic = LibrarySemanticAdapter.adapt(spell);
            var program = SemanticVmLowerer.lowerChecked(semantic, context(spell.id()));
            double compileMicros = medianCompileNanos(spell) / 1_000.0;
            double baseMana = program.manaCost().total();
            int castTicks = (int) Math.ceil(CastingMethod.BARE
                    .baseCastingTicks(program.instructions().size()));
            int effectTicks = durationTicks(spell);
            double quotedUpkeep = Math.max(CastingConfig.DEFAULT_MINIMUM_UPKEEP,
                    program.manaCost().duration());
            double committedUpkeep = effectTicks > 1 ? quotedUpkeep : 0.0;
            int installments = effectTicks > 1
                    ? (effectTicks + PersistentEffectService.UPKEEP_INTERVAL_TICKS - 1)
                            / PersistentEffectService.UPKEEP_INTERVAL_TICKS
                    : 0;
            double perInterval = installments == 0 ? 0.0 : committedUpkeep / installments;
            lines.add(String.format(Locale.ROOT,
                    "%s\t%s\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f"
                            + "\t%d\t%.3f\t%.3f\t%.3f\t%d\t%.3f\t%d\t%d\t%.3f",
                    spell.id(), spell.title(), element(semantic).id(), semantic.instructions().size(),
                    program.instructions().size(), compileMicros,
                    baseMana, baseMana / .75, baseMana / .50, baseMana / .25,
                    castTicks, castTicks / 20.0, quotedUpkeep, committedUpkeep,
                    effectTicks, effectTicks / 20.0,
                    effectTicks > 1 ? PersistentEffectService.UPKEEP_INTERVAL_TICKS : 0,
                    installments, perInterval));
        }
        assertTrue(lines.size() == 6, "the report must contain five spell rows");
        Path report = Path.of("build", "reports", "five-spell-metrics.tsv");
        Files.createDirectories(report.getParent());
        Files.write(report, lines, StandardCharsets.UTF_8);
    }

    private static long medianCompileNanos(SpellDefinition spell) {
        for (int index = 0; index < WARMUP_COMPILES; index++) compile(spell);
        long[] samples = new long[MEASURED_COMPILES];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            compile(spell);
            samples[index] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static void compile(SpellDefinition spell) {
        SemanticVmLowerer.lowerChecked(LibrarySemanticAdapter.adapt(spell), context(spell.id()));
    }

    private static LoweringContext context(String id) {
        return new LoweringContext(id, 0L, Map.of());
    }

    private static int durationTicks(SpellDefinition spell) {
        return spell.program().stream()
                .filter(step -> step.opcode() == LibraryOpcode.SET_DURATION)
                .mapToInt(step -> ((Number) step.operands().get("ticks")).intValue())
                .max().orElse(0);
    }

    private static Element element(SemanticProgram semantic) {
        return semantic.instructions().stream()
                .map(step -> step.opcode().name())
                .filter(name -> name.startsWith("ELEMENT_"))
                .map(name -> Element.fromId(name.substring("ELEMENT_".length())))
                .flatMap(java.util.Optional::stream)
                .findFirst().orElse(Element.ARCANE);
    }
}
