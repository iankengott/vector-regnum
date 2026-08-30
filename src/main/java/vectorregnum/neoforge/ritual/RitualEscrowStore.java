package vectorregnum.neoforge.ritual;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.ritual.CooperativeRitual;

/** Checksummed player-NBT escrow records. Mana and these records save in one player file. */
public final class RitualEscrowStore {
    private static final String HEADER = "vr-ritual-escrow:1";
    public static final int MAX_ESCROWS_PER_PLAYER = 8;

    private final Map<UUID, Escrow> escrows;

    public RitualEscrowStore(Map<UUID, Escrow> escrows) {
        Objects.requireNonNull(escrows, "escrows");
        if (escrows.size() > MAX_ESCROWS_PER_PLAYER) {
            throw new IllegalArgumentException("player ritual escrow cap exceeded");
        }
        this.escrows = Map.copyOf(new LinkedHashMap<>(escrows));
    }

    public static RitualEscrowStore empty() {
        return new RitualEscrowStore(Map.of());
    }

    public Map<UUID, Escrow> escrows() {
        return escrows;
    }

    public Escrow get(UUID ritualId) {
        return escrows.get(ritualId);
    }

    public RitualEscrowStore put(Escrow escrow) {
        Objects.requireNonNull(escrow, "escrow");
        Escrow existing = escrows.get(escrow.ritualId());
        if (existing != null) {
            if (existing.equals(escrow)) return this;
            throw new IllegalStateException("ritual escrow retry changed its terms");
        }
        if (escrows.size() >= MAX_ESCROWS_PER_PLAYER) {
            throw new IllegalStateException("player ritual escrow cap reached");
        }
        LinkedHashMap<UUID, Escrow> updated = new LinkedHashMap<>(escrows);
        updated.put(escrow.ritualId(), escrow);
        return new RitualEscrowStore(updated);
    }

    public RitualEscrowStore remove(UUID ritualId) {
        if (!escrows.containsKey(ritualId)) return this;
        LinkedHashMap<UUID, Escrow> updated = new LinkedHashMap<>(escrows);
        updated.remove(ritualId);
        return updated.isEmpty() ? empty() : new RitualEscrowStore(updated);
    }

    public String encode() {
        StringBuilder body = new StringBuilder(HEADER);
        escrows.values().stream().sorted(java.util.Comparator.comparing(Escrow::ritualId))
                .forEach(escrow -> body.append('\n').append(row(escrow)));
        String content = body.toString();
        return content + "\nsha256:" + checksum(content);
    }

    public static RitualEscrowStore decode(String encoded, CastingPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (encoded == null || encoded.isBlank()) return empty();
        String[] lines = encoded.split("\\n", -1);
        if (lines.length < 2 || !HEADER.equals(lines[0])
                || !lines[lines.length - 1].startsWith("sha256:")) {
            throw new IllegalArgumentException("ritual escrow header or checksum is missing");
        }
        String body = String.join("\n", java.util.Arrays.copyOf(lines, lines.length - 1));
        String expected = lines[lines.length - 1].substring("sha256:".length());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                checksum(body).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("ritual escrow checksum mismatch");
        }
        LinkedHashMap<UUID, Escrow> result = new LinkedHashMap<>();
        for (int index = 1; index < lines.length - 1; index++) {
            Escrow escrow = parseRow(lines[index], policy);
            if (result.put(escrow.ritualId(), escrow) != null) {
                throw new IllegalArgumentException("duplicate player ritual escrow");
            }
        }
        return new RitualEscrowStore(result);
    }

    private static String row(Escrow escrow) {
        return String.join("|", escrow.ritualId().toString(),
                Double.toHexString(escrow.reservedMana()),
                Double.toHexString(escrow.reservedUpkeep()),
                Integer.toString(escrow.loadout().units(ReagentKind.MANA)),
                Integer.toString(escrow.loadout().units(ReagentKind.CASTING_TIME)),
                Integer.toString(escrow.loadout().units(ReagentKind.UPKEEP)),
                Integer.toString(escrow.loadout().units(ReagentKind.INSTABILITY)),
                Integer.toString(escrow.loadout().offeringUnits()));
    }

    private static Escrow parseRow(String row, CastingPolicy policy) {
        String[] fields = row.split("\\|", -1);
        if (fields.length != 8) throw new IllegalArgumentException("malformed ritual escrow row");
        try {
            EnumMap<ReagentKind, Integer> units = new EnumMap<>(ReagentKind.class);
            units.put(ReagentKind.MANA, Integer.parseInt(fields[3]));
            units.put(ReagentKind.CASTING_TIME, Integer.parseInt(fields[4]));
            units.put(ReagentKind.UPKEEP, Integer.parseInt(fields[5]));
            units.put(ReagentKind.INSTABILITY, Integer.parseInt(fields[6]));
            return new Escrow(UUID.fromString(fields[0]), Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]),
                    ReagentLoadout.of(units, Integer.parseInt(fields[7]), policy));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed ritual escrow value", exception);
        }
    }

    private static String checksum(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Escrow(UUID ritualId, double reservedMana, double reservedUpkeep,
            ReagentLoadout loadout) {
        public Escrow {
            Objects.requireNonNull(ritualId, "ritualId");
            Objects.requireNonNull(loadout, "loadout");
            if (!Double.isFinite(reservedMana) || reservedMana < 0.0
                    || reservedMana > CooperativeRitual.MAX_COMMITMENT_MANA
                    || !Double.isFinite(reservedUpkeep) || reservedUpkeep < 0.0
                    || reservedUpkeep > CooperativeRitual.MAX_COMMITMENT_MANA) {
                throw new IllegalArgumentException("ritual escrow mana is outside its bound");
            }
        }

        public double totalMana() {
            return reservedMana + reservedUpkeep;
        }
    }
}
