package vectorregnum.neoforge.presentation;

import vectorregnum.core.presentation.PresentationAccessibility;

/** Optional client-only enhancement layer. The built-in renderer always runs first. */
interface ClientPresentationBackend {
    String id();

    void cueStarted(PresentationCueContext cue, PresentationAccessibility accessibility);

    void cueTick(PresentationCueContext cue, PresentationAccessibility accessibility,
            int localAge, int duration, double envelope);

    void cueEnded(long cueId);

    void resourceReloaded();

    void clear();
}
