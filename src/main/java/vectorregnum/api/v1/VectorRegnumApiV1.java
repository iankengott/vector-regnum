package vectorregnum.api.v1;

import java.util.List;

/** Stable, optional, loader-neutral Vector-Regnum integration metadata. */
public final class VectorRegnumApiV1 {
    public static final int VERSION = 1;
    public static final boolean OPTIONAL = true;

    /** Domains are part of the wire/API contract; their order must not change. */
    public static final List<String> DOMAINS = List.of(
            "origins", "combat", "progression", "world_story", "administration", "modpack");

    /** The process-wide registry used by the NeoForge adapter and companions. */
    public static final IntegrationRegistry REGISTRY = new IntegrationRegistry();

    private VectorRegnumApiV1() {
    }

    /** Returns whether this API can satisfy the requested major version. */
    public static boolean supports(int version) {
        return version == VERSION;
    }

    /** Returns the stable domain order. */
    public static List<String> domains() {
        return DOMAINS;
    }

    /** Returns the one process-wide v1 registration registry. */
    public static IntegrationRegistry registry() {
        return REGISTRY;
    }
}
