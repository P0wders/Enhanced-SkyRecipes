package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.compat.skyblocker.SkyblockerPrices;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.render.item.ItemStackBuilder;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Resolves the {@code price:ITEM} calculator operand to a coin value.
 *
 * <p>The name is matched, in order, against: the SkyBlock item id ({@code price:wither_shield_scroll}),
 * the item's display name with spaces as underscores ({@code price:wither_shield}), then the
 * shortest display name starting with it ({@code price:hyperio}). The price is Skyblocker's
 * Bazaar instant-buy price for Bazaar items, otherwise the lowest BIN.</p>
 */
public final class ItemPriceLookup {

    private static final boolean SKYBLOCKER = FabricLoader.getInstance().isModLoaded("skyblocker");

    /** Normalized display name → internal names, rebuilt whenever the registry is reloaded. */
    private record NameIndex(ItemRegistry registry, Map<String, List<String>> byName) { }

    private static volatile NameIndex index;
    private static volatile boolean disabled;

    private ItemPriceLookup() {
    }

    public static Optional<BigDecimal> price(String name) {
        if (!SKYBLOCKER || disabled || name == null || name.isEmpty()) {
            return Optional.empty();
        }
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) {
            return Optional.empty();
        }
        try {
            String key = normalize(name);

            // 1. Exact item id.
            Optional<BigDecimal> price = priceOf(registry, key);
            if (price.isPresent()) return price;

            // 2. Exact display name.
            NameIndex names = indexFor(registry);
            for (String internalName : names.byName().getOrDefault(key, List.of())) {
                price = priceOf(registry, internalName);
                if (price.isPresent()) return price;
            }

            // 3. Shortest display name starting with what was typed.
            List<String> prefixMatches = new ArrayList<>();
            for (String displayKey : names.byName().keySet()) {
                if (displayKey.startsWith(key)) prefixMatches.add(displayKey);
            }
            prefixMatches.sort((a, b) -> a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b));
            for (String displayKey : prefixMatches) {
                for (String internalName : names.byName().get(displayKey)) {
                    price = priceOf(registry, internalName);
                    if (price.isPresent()) return price;
                }
            }
            return Optional.empty();
        } catch (Throwable t) {
            disabled = true;
            SkyRecipes.LOGGER.error("price: lookups disabled: could not read Skyblocker prices", t);
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> priceOf(ItemRegistry registry, String internalName) {
        NeuItem item = registry.getByInternalNameCanonical(internalName).orElse(null);
        if (item == null) {
            return Optional.empty();
        }
        // Build the stack in Hypixel's shape so Skyblocker maps it to the right price key
        // (enchanted books, pets, runes, ... all use different keys than the NEU id).
        ItemStack stack = ItemStackBuilder.build(item);
        OptionalDouble price = SkyblockerPrices.price(stack);
        return price.isPresent() ? Optional.of(BigDecimal.valueOf(price.getAsDouble())) : Optional.empty();
    }

    private static NameIndex indexFor(ItemRegistry registry) {
        NameIndex current = index;
        if (current != null && current.registry() == registry) {
            return current;
        }
        Map<String, List<String>> byName = new HashMap<>();
        for (NeuItem item : registry.getCanonicalItems()) {
            String displayKey = normalize(TextUtil.stripColorCodes(item.displayName()));
            if (!displayKey.isEmpty()) {
                byName.computeIfAbsent(displayKey, k -> new ArrayList<>(1)).add(item.internalName());
            }
        }
        NameIndex built = new NameIndex(registry, byName);
        index = built;
        return built;
    }

    /** Upper-case, with every run of non-alphanumeric characters turned into one underscore. */
    private static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingUnderscore = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (pendingUnderscore && !out.isEmpty()) out.append('_');
                pendingUnderscore = false;
                out.append(Character.toUpperCase(c));
            } else {
                pendingUnderscore = true;
            }
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }
}
