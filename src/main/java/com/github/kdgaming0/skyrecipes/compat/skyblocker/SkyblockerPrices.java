package com.github.kdgaming0.skyrecipes.compat.skyblocker;

import de.hysky.skyblocker.injected.SkyblockerStack;
import de.hysky.skyblocker.utils.ItemUtils;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalDouble;

/**
 * Reads market prices from Skyblocker's already-downloaded Bazaar and lowest-BIN data.
 *
 * <p>Only reference this class after checking that Skyblocker is loaded (see
 * {@link com.github.kdgaming0.skyrecipes.rrv.overlay.PriceSort}); otherwise the Skyblocker
 * classes it imports do not exist and class loading fails.</p>
 *
 * <p>SkyRecipes builds its stacks in the shape Hypixel sends (base id plus side fields), so
 * Skyblocker resolves the same API id for them as for a real in-game item.</p>
 */
public final class SkyblockerPrices {

    private SkyblockerPrices() {
    }

    /**
     * Bazaar instant-buy price for Bazaar items, otherwise the lowest BIN. Empty when the item
     * has no market price or Skyblocker has not loaded (or was configured not to load) the data.
     */
    public static OptionalDouble price(ItemStack stack) {
        String apiId = ((SkyblockerStack) (Object) stack).getSkyblockApiId();
        return ItemUtils.getItemPrice(apiId, true);
    }
}
