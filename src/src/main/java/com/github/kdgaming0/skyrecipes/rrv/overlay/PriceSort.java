package com.github.kdgaming0.skyrecipes.rrv.overlay;

import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig.PriceSortMode;
import com.github.kdgaming0.skyrecipes.compat.skyblocker.SkyblockerPrices;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Optional price ordering for the item list, chosen with the "Sort Item List by Price" setting.
 *
 * <p>Prices come from Skyblocker (Bazaar instant-buy price, else lowest BIN). Items with no
 * known price always go last, in their original relative order, whichever direction is chosen.
 * Without Skyblocker installed the setting has no effect.</p>
 */
public final class PriceSort {

    private static final boolean SKYBLOCKER = FabricLoader.getInstance().isModLoaded("skyblocker");
    private static volatile boolean disabled;

    private PriceSort() {
    }

    /** Returns a new, sorted list; the input is never mutated. */
    public static List<ItemStack> apply(List<ItemStack> items, PriceSortMode mode) {
        if (mode == null || mode == PriceSortMode.DEFAULT || !SKYBLOCKER || disabled || items.size() < 2) {
            return items;
        }
        try {
            List<Priced> priced = new ArrayList<>(items.size());
            for (ItemStack stack : items) {
                OptionalDouble price = SkyblockerPrices.price(stack);
                priced.add(new Priced(stack, price.isPresent() ? price.getAsDouble() : Double.NaN));
            }
            Comparator<Priced> byPrice = Comparator.comparingDouble(Priced::price);
            if (mode == PriceSortMode.PRICIEST) byPrice = byPrice.reversed();
            // List.sort is stable, so equal prices and unpriced items keep their existing order.
            priced.sort(Comparator.comparing(Priced::unpriced).thenComparing(byPrice));

            List<ItemStack> sorted = new ArrayList<>(priced.size());
            for (Priced p : priced) sorted.add(p.stack());
            return sorted;
        } catch (Throwable t) {
            // Skyblocker API drift: stop trying rather than break search on every refresh.
            disabled = true;
            SkyRecipes.LOGGER.error("Price sorting disabled: could not read Skyblocker prices", t);
            return items;
        }
    }

    private record Priced(ItemStack stack, double price) {
        boolean unpriced() {
            return Double.isNaN(price);
        }
    }
}
