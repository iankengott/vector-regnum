package vectorregnum.neoforge.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Explicit bounded wire format for server-authored teaching traces. */
public final class PonderTimelineCodec {
    public static final int MAX_ENCODED_LENGTH = 196_608;

    private PonderTimelineCodec() {
    }

    public static String encode(PonderTimeline timeline) {
        JsonObject root = new JsonObject();
        root.addProperty("id", timeline.id());
        root.addProperty("title", timeline.title());
        JsonArray steps = new JsonArray();
        for (PonderTimeline.Step step : timeline.steps()) {
            JsonObject encodedStep = new JsonObject();
            encodedStep.addProperty("index", step.index());
            encodedStep.addProperty("duration", step.durationTicks());
            encodedStep.addProperty("phase", step.phase().name());
            encodedStep.addProperty("title", step.title());
            encodedStep.addProperty("narration", step.narration());
            JsonArray cues = new JsonArray();
            for (PonderTimeline.Cue cue : step.cues()) {
                JsonObject encodedCue = new JsonObject();
                encodedCue.addProperty("type", cue.type().name());
                cue.source().ifPresent(source -> {
                    JsonObject encodedSource = new JsonObject();
                    encodedSource.addProperty("sourceIndex", source.sourceIndex());
                    encodedSource.addProperty("ring", source.ring());
                    encodedSource.addProperty("slot", source.clockwiseSlot());
                    encodedSource.addProperty("sigil", source.sigilId());
                    encodedCue.add("source", encodedSource);
                });
                JsonObject data = new JsonObject();
                cue.data().forEach(data::addProperty);
                encodedCue.add("data", data);
                cues.add(encodedCue);
            }
            encodedStep.add("cues", cues);
            steps.add(encodedStep);
        }
        root.add("steps", steps);
        String encoded = root.toString();
        if (encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("ponder trace exceeds wire limit");
        }
        return encoded;
    }

    public static PonderTimeline decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("invalid ponder trace payload");
        }
        try {
            JsonObject root = JsonParser.parseString(encoded).getAsJsonObject();
            JsonArray encodedSteps = requiredArray(root, "steps");
            if (encodedSteps.isEmpty() || encodedSteps.size() > PonderTimeline.MAX_STEPS) {
                throw new IllegalArgumentException("invalid ponder step count");
            }
            List<PonderTimeline.Step> steps = new ArrayList<>(encodedSteps.size());
            for (JsonElement stepElement : encodedSteps) {
                JsonObject step = stepElement.getAsJsonObject();
                JsonArray encodedCues = requiredArray(step, "cues");
                if (encodedCues.isEmpty() || encodedCues.size() > PonderTimeline.MAX_CUES_PER_STEP) {
                    throw new IllegalArgumentException("invalid ponder cue count");
                }
                List<PonderTimeline.Cue> cues = new ArrayList<>(encodedCues.size());
                for (JsonElement cueElement : encodedCues) {
                    JsonObject cue = cueElement.getAsJsonObject();
                    Optional<PonderTimeline.SourceRef> source = Optional.empty();
                    if (cue.has("source")) {
                        JsonObject encodedSource = cue.getAsJsonObject("source");
                        source = Optional.of(new PonderTimeline.SourceRef(
                                requiredInt(encodedSource, "sourceIndex"),
                                requiredInt(encodedSource, "ring"),
                                requiredInt(encodedSource, "slot"),
                                requiredString(encodedSource, "sigil")));
                    }
                    JsonObject encodedData = cue.getAsJsonObject("data");
                    if (encodedData == null || encodedData.size() > PonderTimeline.MAX_CUE_DATA_ENTRIES) {
                        throw new IllegalArgumentException("invalid ponder cue data");
                    }
                    Map<String, String> data = new LinkedHashMap<>();
                    encodedData.entrySet().forEach(entry ->
                            data.put(entry.getKey(), entry.getValue().getAsString()));
                    cues.add(new PonderTimeline.Cue(
                            PonderTimeline.CueType.valueOf(requiredString(cue, "type")), source, data));
                }
                steps.add(new PonderTimeline.Step(requiredInt(step, "index"),
                        requiredInt(step, "duration"),
                        PonderTimeline.Phase.valueOf(requiredString(step, "phase")),
                        requiredString(step, "title"), requiredString(step, "narration"), cues));
            }
            return new PonderTimeline(requiredString(root, "id"),
                    requiredString(root, "title"), steps);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed ponder trace payload", exception);
        }
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing ponder field " + key);
        }
        return object.get(key).getAsString();
    }

    private static int requiredInt(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing ponder field " + key);
        }
        return object.get(key).getAsInt();
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException("missing ponder array " + key);
        }
        return object.getAsJsonArray(key);
    }
}
