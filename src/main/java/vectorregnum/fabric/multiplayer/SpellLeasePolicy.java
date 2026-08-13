package vectorregnum.fabric.multiplayer;

/** Pure fail-closed lifecycle decision used before each server-side VM tick. */
public final class SpellLeasePolicy {
    private SpellLeasePolicy() { }

    public static boolean shouldContinue(boolean ownerConnected, boolean ownerAlive,
            boolean sameDimension, boolean ownerChunkLoaded) {
        return ownerConnected && ownerAlive && sameDimension && ownerChunkLoaded;
    }
}
