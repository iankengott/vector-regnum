package vectorregnum.fabric.ponder;

import java.util.Objects;

/** Small client-facing player with pause, replay, step, and scrub controls. */
public final class PonderController {
    private PonderTimeline timeline;
    private int stepIndex;
    private int tickInStep;
    private boolean playing;

    public PonderController(PonderTimeline timeline) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
    }

    public PonderTimeline timeline() { return timeline; }
    public PonderTimeline.Step currentStep() { return timeline.steps().get(stepIndex); }
    public int stepIndex() { return stepIndex; }
    public int tickInStep() { return tickInStep; }
    public boolean playing() { return playing; }
    public boolean finished() {
        return stepIndex == timeline.steps().size() - 1
                && tickInStep >= currentStep().durationTicks() - 1;
    }

    public void play() { playing = true; }
    public void pause() { playing = false; }

    /** Advances one client tick and returns true only when the visible step changes. */
    public boolean tick() {
        if (!playing || finished()) {
            if (finished()) playing = false;
            return false;
        }
        tickInStep++;
        if (tickInStep < currentStep().durationTicks()) return false;
        tickInStep = 0;
        stepIndex++;
        return true;
    }

    public void stepForward() { seekStep(Math.min(stepIndex + 1, timeline.steps().size() - 1)); }
    public void stepBack() { seekStep(Math.max(0, stepIndex - 1)); }
    public void replay() { stepIndex = 0; tickInStep = 0; playing = true; }

    /**
     * Replaces an in-progress server trace without resetting deliberate
     * scrubbing. A viewer already following the tail stays attached to it.
     */
    public void replaceTimeline(PonderTimeline replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (!timeline.id().equals(replacement.id())) {
            timeline = replacement;
            stepIndex = 0;
            tickInStep = 0;
            playing = true;
            return;
        }
        boolean followedTail = stepIndex == timeline.steps().size() - 1;
        timeline = replacement;
        if (followedTail && playing) {
            stepIndex = replacement.steps().size() - 1;
        } else {
            stepIndex = Math.min(stepIndex, replacement.steps().size() - 1);
        }
        tickInStep = Math.min(tickInStep, currentStep().durationTicks() - 1);
    }

    public void seekStep(int requestedStep) {
        if (requestedStep < 0 || requestedStep >= timeline.steps().size()) {
            throw new IllegalArgumentException("Ponder step outside timeline");
        }
        stepIndex = requestedStep;
        tickInStep = 0;
    }
}
