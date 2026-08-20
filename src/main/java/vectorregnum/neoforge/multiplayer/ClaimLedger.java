package vectorregnum.neoforge.multiplayer;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable, bounded spell-claim state persisted on each server world. */
public record ClaimLedger(int schemaVersion, List<Claim> claims) {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_CLAIMS_PER_OWNER = 16;
    public static final int MAX_WORLD_CLAIMS = 2_048;
    public static final Codec<ClaimLedger> CODEC = Codec.STRING.listOf().xmap(
            ClaimLedger::decode, ClaimLedger::encode);
    public static final ClaimLedger EMPTY = new ClaimLedger(CURRENT_SCHEMA, List.of());

    public ClaimLedger {
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        if (claims.size() > MAX_WORLD_CLAIMS) {
            claims = claims.subList(0, MAX_WORLD_CLAIMS);
        }
    }

    public Optional<Claim> at(ClaimKey key) {
        return claims.stream().filter(claim -> claim.key().equals(key)).findFirst();
    }

    public Change claim(ClaimKey key, UUID owner, String team, Access access) {
        Objects.requireNonNull(owner, "owner");
        if (at(key).isPresent()) return new Change(this, false, "That chunk is already claimed");
        if (claims.size() >= MAX_WORLD_CLAIMS) {
            return new Change(this, false, "This world has reached the spell-claim limit");
        }
        long ownerCount = claims.stream().filter(claim -> claim.owner().equals(owner)).count();
        if (ownerCount >= MAX_CLAIMS_PER_OWNER) {
            return new Change(this, false, "You have reached the 16-chunk spell-claim limit");
        }
        List<Claim> updated = new ArrayList<>(claims);
        updated.add(new Claim(key, owner, normalizedTeam(team), access));
        updated.sort(Comparator.comparing(claim -> claim.key().stableId()));
        return new Change(new ClaimLedger(CURRENT_SCHEMA, updated), true, "Spell claim created");
    }

    public Change release(ClaimKey key, UUID actor, boolean operator) {
        Optional<Claim> existing = at(key);
        if (existing.isEmpty()) return new Change(this, false, "This chunk is not claimed");
        if (!operator && !existing.orElseThrow().owner().equals(actor)) {
            return new Change(this, false, "Only the claim owner can release this chunk");
        }
        return new Change(new ClaimLedger(CURRENT_SCHEMA,
                claims.stream().filter(claim -> !claim.key().equals(key)).toList()),
                true, "Spell claim released");
    }

    public boolean permits(ClaimKey key, UUID actor, String currentTeam, boolean operator) {
        if (operator) return true;
        Optional<Claim> existing = at(key);
        if (existing.isEmpty()) return true;
        Claim claim = existing.orElseThrow();
        if (claim.owner().equals(actor)) return true;
        return claim.access() == Access.TEAM && !claim.team().isEmpty()
                && claim.team().equals(normalizedTeam(currentTeam));
    }

    public ClaimLedger migrated() {
        if (schemaVersion == CURRENT_SCHEMA) return this;
        return new ClaimLedger(CURRENT_SCHEMA, claims);
    }

    private static List<String> encode(ClaimLedger ledger) {
        List<String> encoded = new ArrayList<>();
        encoded.add("schema=" + CURRENT_SCHEMA);
        ledger.claims.stream().limit(MAX_WORLD_CLAIMS).forEach(claim -> encoded.add(claim.encode()));
        return List.copyOf(encoded);
    }

    private static ClaimLedger decode(List<String> encoded) {
        int schema = 1;
        int start = 0;
        if (!encoded.isEmpty() && encoded.getFirst().startsWith("schema=")) {
            try {
                schema = Integer.parseInt(encoded.getFirst().substring("schema=".length()));
            } catch (NumberFormatException ignored) {
                schema = 1;
            }
            start = 1;
        }
        List<Claim> claims = new ArrayList<>();
        for (int index = start; index < encoded.size() && claims.size() < MAX_WORLD_CLAIMS; index++) {
            Claim.decode(encoded.get(index), schema).ifPresent(candidate -> {
                if (claims.stream().noneMatch(existing -> existing.key().equals(candidate.key()))) {
                    claims.add(candidate);
                }
            });
        }
        return new ClaimLedger(schema, claims).migrated();
    }

    private static String normalizedTeam(String team) {
        if (team == null) return "";
        String normalized = team.replace("|", "").trim();
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    public enum Access { OWNER_ONLY, TEAM }

    public record ClaimKey(String dimension, int chunkX, int chunkZ) {
        public ClaimKey {
            dimension = Objects.requireNonNull(dimension, "dimension").trim();
            if (dimension.isEmpty() || dimension.length() > 128 || dimension.contains("|")) {
                throw new IllegalArgumentException("invalid claim dimension");
            }
        }

        public String stableId() { return dimension + ":" + chunkX + ":" + chunkZ; }
    }

    public record Claim(ClaimKey key, UUID owner, String team, Access access) {
        public Claim {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(owner, "owner");
            team = normalizedTeam(team);
            Objects.requireNonNull(access, "access");
            if (access == Access.TEAM && team.isEmpty()) access = Access.OWNER_ONLY;
        }

        private String encode() {
            return key.dimension() + "|" + key.chunkX() + "|" + key.chunkZ() + "|"
                    + owner + "|" + access.name() + "|" + team;
        }

        private static Optional<Claim> decode(String value, int schema) {
            try {
                String[] fields = value.split("\\|", -1);
                // Schema 1 stored dimension|x|z|owner and implied owner-only access.
                if (fields.length < 4) return Optional.empty();
                Access access = fields.length >= 5 ? Access.valueOf(fields[4]) : Access.OWNER_ONLY;
                String team = fields.length >= 6 ? fields[5] : "";
                return Optional.of(new Claim(new ClaimKey(fields[0], Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2])), UUID.fromString(fields[3]), team, access));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public record Change(ClaimLedger ledger, boolean accepted, String message) { }
}
