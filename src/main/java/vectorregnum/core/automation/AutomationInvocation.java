package vectorregnum.core.automation;

import java.util.Objects;
import java.util.UUID;

/** Immutable message crossing into the server-tick-owned automation runtime. */
public record AutomationInvocation(UUID owner, AutomationEndpoint endpoint,
        TriggerCause cause, AutomationDataFrame data) {
    public enum TriggerCause { REDSTONE, REMOTE, DATA_BRIDGE }

    public AutomationInvocation {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(data, "data");
    }
}
