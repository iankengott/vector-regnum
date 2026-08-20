package vectorregnum.neoforge.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import vectorregnum.neoforge.VectorRegnumMod;

/**
 * Live registration gate for the NeoForge migration.
 *
 * <p>The expected IDs come from the shipped parity resource.  Every assertion
 * below observes the loader's registries, the built creative-tab contents, the
 * command dispatcher, or NeoForge's payload registry; no production registry
 * constant is copied into this test.</p>
 */
@GameTestHolder(VectorRegnumMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Priority20RegistrationParityGameTests {
    private static final ResourceLocation PARITY_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(VectorRegnumMod.MOD_ID,
                    "registration_parity.json");

    private Priority20RegistrationParityGameTests() {
    }

    @GameTest(template = "empty")
    public static void liveRegistrationParityMatchesManifest(GameTestHelper context) {
        List<String> failures = new ArrayList<>();
        JsonObject manifest = readManifest(context, failures);
        if (manifest == null) {
            context.fail(String.join("; ", failures));
            return;
        }

        if (!manifest.has("schema") || manifest.get("schema").getAsInt() != 1) {
            failures.add("registration parity manifest schema must be 1");
        }

        JsonObject registries = object(manifest, "registries", failures);
        if (registries != null) {
            checkRegistry(registries, "blocks", BuiltInRegistries.BLOCK, "block", failures);
            checkRegistry(registries, "items", BuiltInRegistries.ITEM, "item", failures);
            checkRegistry(registries, "block_entity_types", BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    "block entity", failures);
        }

        checkAttachmentTypes(manifest, failures);
        checkPayloads(manifest, failures);
        checkCreativeTabs(context, manifest, failures);
        checkCommands(context, manifest, failures);

        if (!failures.isEmpty()) {
            context.fail("Priority 20 live registration parity failed: "
                    + String.join("; ", failures));
            return;
        }
        context.succeed();
    }

    private static JsonObject readManifest(GameTestHelper context, List<String> failures) {
        ResourceManager resources = context.getLevel().getServer().getResourceManager();
        try (Reader reader = resources.openAsReader(PARITY_RESOURCE)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                failures.add(PARITY_RESOURCE + " must contain a JSON object");
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            failures.add("could not load " + PARITY_RESOURCE + ": " + exception.getMessage());
            return null;
        }
    }

    private static JsonObject object(JsonObject parent, String key, List<String> failures) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            failures.add("manifest section '" + key + "' is missing or not an object");
            return null;
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key, List<String> failures) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) {
            failures.add("manifest section '" + key + "' is missing or not an array");
            return null;
        }
        return value.getAsJsonArray();
    }

    private static void checkRegistry(JsonObject registries, String key, Registry<?> registry,
            String label, List<String> failures) {
        JsonArray expected = array(registries, key, failures);
        if (expected == null) {
            return;
        }
        for (JsonElement element : expected) {
            String id = stringId(element, label, failures);
            if (id == null) {
                continue;
            }
            ResourceLocation location = parseId(id, label, failures);
            if (location != null && !registry.containsKey(location)) {
                failures.add("missing live " + label + " registration " + id);
            }
        }
    }

    private static void checkAttachmentTypes(JsonObject manifest, List<String> failures) {
        JsonArray expected = array(manifest, "attachments", failures);
        if (expected == null) {
            return;
        }
        Registry<AttachmentType<?>> registry = NeoForgeRegistries.ATTACHMENT_TYPES;
        for (JsonElement element : expected) {
            String id = stringId(element, "attachment", failures);
            if (id == null) {
                continue;
            }
            ResourceLocation location = parseId(id, "attachment", failures);
            if (location != null && !registry.containsKey(location)) {
                failures.add("missing live attachment registration " + id);
            }
        }
    }

    private static void checkPayloads(JsonObject manifest, List<String> failures) {
        JsonObject expected = object(manifest, "payloads", failures);
        if (expected == null) return;
        checkPayloadDirection(expected, "serverbound",
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND, failures);
        checkPayloadDirection(expected, "clientbound",
                net.minecraft.network.protocol.PacketFlow.CLIENTBOUND, failures);
    }

    private static void checkPayloadDirection(JsonObject payloads, String key,
            net.minecraft.network.protocol.PacketFlow flow, List<String> failures) {
        JsonArray expected = array(payloads, key, failures);
        if (expected == null) return;
        for (JsonElement element : expected) {
            String id = stringId(element, "payload", failures);
            if (id == null) {
                continue;
            }
            ResourceLocation location = parseId(id, "payload", failures);
            if (location == null) {
                continue;
            }
            if (NetworkRegistry.getCodec(location, ConnectionProtocol.PLAY, flow) == null) {
                failures.add("missing live " + key + " play payload registration " + id);
            }
        }
    }

    private static void checkCreativeTabs(GameTestHelper context, JsonObject manifest,
            List<String> failures) {
        JsonObject expectedTabs = object(manifest, "creative_tabs", failures);
        if (expectedTabs == null) {
            return;
        }

        // This invokes the same vanilla tab build path that the client uses;
        // EventHooks dispatches each BuildCreativeModeTabContentsEvent to the
        // registered NeoForge content listeners.
        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, true,
                context.getLevel().registryAccess());

        for (var entry : expectedTabs.entrySet()) {
            ResourceLocation tabId = parseId(entry.getKey(), "creative tab", failures);
            if (tabId == null || !entry.getValue().isJsonArray()) {
                if (tabId != null) {
                    failures.add("creative tab " + entry.getKey() + " is not an array");
                }
                continue;
            }
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
            if (tab == null) {
                failures.add("missing live creative tab " + entry.getKey());
                continue;
            }
            Set<String> actualItems = itemIds(tab.getDisplayItems());
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                String id = stringId(element, "creative-tab item", failures);
                if (id != null && !actualItems.contains(id)) {
                    failures.add("creative tab " + entry.getKey()
                            + " is missing live item " + id);
                }
            }
        }
    }

    private static Set<String> itemIds(Collection<ItemStack> stacks) {
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : stacks) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                ids.add(id.toString());
            }
        }
        return ids;
    }

    private static void checkCommands(GameTestHelper context, JsonObject manifest,
            List<String> failures) {
        JsonArray expected = array(manifest, "command_roots", failures);
        if (expected == null) {
            return;
        }
        var root = context.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (JsonElement element : expected) {
            String id = stringId(element, "command root", failures);
            if (id != null && root.getChild(id) == null) {
                failures.add("missing live command root " + id);
            }
        }
    }

    private static String stringId(JsonElement element, String label, List<String> failures) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            failures.add(label + " entry is not a string");
            return null;
        }
        return element.getAsString();
    }

    private static ResourceLocation parseId(String id, String label, List<String> failures) {
        try {
            return ResourceLocation.parse(id);
        } catch (RuntimeException exception) {
            failures.add("invalid " + label + " id " + id);
            return null;
        }
    }
}
