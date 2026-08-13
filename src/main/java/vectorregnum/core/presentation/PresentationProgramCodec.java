package vectorregnum.core.presentation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.vm2.Opcode;

/** Versioned, bounded wire codec for immutable presentation programs. */
public final class PresentationProgramCodec {
    public static final int MAX_ENCODED_LENGTH = 65_536;
    private static final int VERSION = 1;

    private PresentationProgramCodec() { }

    public static String encode(PresentationProgram program) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(VERSION);
                output.writeUTF(program.id());
                output.writeLong(program.deterministicSeed());
                PresentationBudget budget = program.budget();
                output.writeInt(budget.maximumCues());
                output.writeInt(budget.maximumDurationTicks());
                writeCost(output, budget.maximumCost());
                output.writeInt(program.instructions().size());
                for (PresentationInstruction instruction : program.instructions()) {
                    writeInstruction(output, instruction);
                }
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            if (encoded.length() > MAX_ENCODED_LENGTH) {
                throw new IllegalArgumentException("presentation program exceeds wire budget");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("could not encode in-memory presentation program", exception);
        }
    }

    public static PresentationProgram decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("invalid encoded presentation program");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readUnsignedByte() != VERSION) {
                    throw new IllegalArgumentException("unsupported presentation wire version");
                }
                String id = input.readUTF();
                long seed = input.readLong();
                PresentationBudget budget = new PresentationBudget(input.readInt(), input.readInt(),
                        readCost(input));
                int count = input.readInt();
                if (count < 0 || count > budget.maximumCues() || count > 64) {
                    throw new IllegalArgumentException("invalid presentation cue count");
                }
                List<PresentationInstruction> instructions = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    instructions.add(readInstruction(input));
                }
                if (input.available() != 0) {
                    throw new IllegalArgumentException("trailing presentation wire data");
                }
                return new PresentationProgram(id, seed, instructions, budget);
            }
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("malformed presentation program", exception);
        }
    }

    private static void writeInstruction(DataOutputStream output,
            PresentationInstruction instruction) throws IOException {
        PresentationTrigger trigger = instruction.trigger();
        output.writeByte(trigger.kind().ordinal());
        output.writeByte(trigger.opcode().map(Enum::ordinal).orElse(-1));
        output.writeByte(trigger.semanticOpcode().map(Enum::ordinal).orElse(-1));
        output.writeByte(instruction.phase().ordinal());
        output.writeByte(instruction.cueKind().ordinal());
        output.writeUTF(instruction.rendererId());
        output.writeByte(instruction.binding().ordinal());
        output.writeInt(instruction.startOffsetTicks());
        output.writeInt(instruction.durationTicks());
        output.writeDouble(instruction.intensity());
        output.writeBoolean(instruction.truthLayer());
        output.writeByte(instruction.parameters().size());
        instruction.parameters().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> writeParameter(output, entry));
        writeCost(output, instruction.cost());
    }

    private static PresentationInstruction readInstruction(DataInputStream input) throws IOException {
        PresentationTrigger.Kind kind = ordinal(PresentationTrigger.Kind.values(), input.readUnsignedByte());
        int vmIndex = input.readByte();
        int semanticIndex = input.readByte();
        Optional<Opcode> opcode = vmIndex < 0 ? Optional.empty()
                : Optional.of(ordinal(Opcode.values(), vmIndex));
        Optional<SemanticOpcode> semanticOpcode = semanticIndex < 0 ? Optional.empty()
                : Optional.of(ordinal(SemanticOpcode.values(), semanticIndex));
        PresentationTrigger trigger = new PresentationTrigger(kind, opcode, semanticOpcode);
        PresentationPhase phase = ordinal(PresentationPhase.values(), input.readUnsignedByte());
        PresentationCueKind cueKind = ordinal(PresentationCueKind.values(), input.readUnsignedByte());
        String rendererId = input.readUTF();
        PresentationBinding binding = ordinal(PresentationBinding.values(), input.readUnsignedByte());
        int offset = input.readInt();
        int duration = input.readInt();
        double intensity = input.readDouble();
        boolean truth = input.readBoolean();
        int parameterCount = input.readUnsignedByte();
        if (parameterCount > 16) throw new IllegalArgumentException("too many wire parameters");
        Map<String, Double> parameters = new LinkedHashMap<>();
        for (int index = 0; index < parameterCount; index++) {
            String name = input.readUTF();
            if (parameters.put(name, input.readDouble()) != null) {
                throw new IllegalArgumentException("duplicate wire parameter");
            }
        }
        return new PresentationInstruction(trigger, phase, cueKind, rendererId, binding,
                offset, duration, intensity, truth, parameters, readCost(input));
    }

    private static void writeParameter(DataOutputStream output, Map.Entry<String, Double> entry) {
        try {
            output.writeUTF(entry.getKey());
            output.writeDouble(entry.getValue());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeCost(DataOutputStream output, PresentationCost cost) throws IOException {
        output.writeInt(cost.emitters());
        output.writeInt(cost.particlesPerSecond());
        output.writeInt(cost.meshVertices());
        output.writeInt(cost.lights());
        output.writeInt(cost.sounds());
        output.writeInt(cost.screenPasses());
        output.writeInt(cost.repetitions());
    }

    private static PresentationCost readCost(DataInputStream input) throws IOException {
        return new PresentationCost(input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), input.readInt(), input.readInt());
    }

    private static <T> T ordinal(T[] values, int index) {
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("invalid enum ordinal");
        return values[index];
    }
}
