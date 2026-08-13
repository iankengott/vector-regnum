package vectorregnum.core.presentation;

/** Optional presentation bridge; VM execution deliberately treats sink failures as non-authoritative. */
@FunctionalInterface
public interface ExecutionEventSink {
    ExecutionEventSink NOOP = event -> { };

    void accept(ExecutionEvent event);
}
