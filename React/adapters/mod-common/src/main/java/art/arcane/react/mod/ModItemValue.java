package art.arcane.react.mod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * React's recipe-derived material value of an item on mods, computed the same way as the Bukkit MaterialValue.
 * Values are kept for the lifetime of the server they were computed on.
 */
final class ModItemValue {
    private static final double BASE_VALUE = 100D;
    private static final int MAX_RECIPE_LIST_PRECAUTION = 50;
    private static final Map<Item, Double> VALUES = new HashMap<>();
    private static final Map<Item, List<Recipe>> RECIPES = new HashMap<>();
    private static Reference<MinecraftServer> indexed = new WeakReference<>(null);

    private ModItemValue() {
    }

    record Input(Item item, int count) {
    }

    record Recipe(Item output, int outputCount, List<Input> inputs) {
    }

    static Recipe shaped(ItemStack result, List<Item> slots) {
        if (result.isEmpty()) {
            return null;
        }
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Item slot : slots) {
            if (slot != null && slot != Items.AIR) {
                counts.merge(slot, 1, Integer::sum);
            }
        }
        List<Input> inputs = new ArrayList<>(counts.size());
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            inputs.add(new Input(entry.getKey(), entry.getValue()));
        }
        return new Recipe(result.getItem(), result.getCount(), List.copyOf(inputs));
    }

    static Recipe shapeless(ItemStack result, List<Item> ingredients) {
        if (result.isEmpty()) {
            return null;
        }
        List<Input> inputs = new ArrayList<>(ingredients.size());
        for (Item ingredient : ingredients) {
            if (ingredient != null) {
                inputs.add(new Input(ingredient, 1));
            }
        }
        return new Recipe(result.getItem(), result.getCount(), List.copyOf(inputs));
    }

    static double value(MinecraftServer server, Item item) {
        if (indexed.get() != server) {
            index(server);
        }
        try {
            return value(item, new HashSet<>());
        } catch (RuntimeException e) {
            return 1D;
        }
    }

    private static void index(MinecraftServer server) {
        VALUES.clear();
        RECIPES.clear();
        for (Recipe recipe : VanillaApi.recipes(server)) {
            RECIPES.computeIfAbsent(recipe.output(), key -> new ArrayList<>()).add(recipe);
        }
        indexed = new WeakReference<>(server);
    }

    private static double value(Item item, Set<Recipe> ignore) {
        Double known = VALUES.get(item);
        if (known == null) {
            double value = BASE_VALUE;
            List<Recipe> recipes = RECIPES.get(item);
            if (recipes != null) {
                double total = 0D;
                int counted = 0;
                for (Recipe recipe : recipes) {
                    if (!ignore.add(recipe)) {
                        continue;
                    }
                    double recipeValue = BASE_VALUE;
                    for (Input input : recipe.inputs()) {
                        recipeValue += value(input.item(), ignore);
                    }
                    total += recipeValue / recipe.outputCount();
                    counted++;
                }
                if (counted > 0) {
                    value += total / counted;
                }
                if (value > MAX_RECIPE_LIST_PRECAUTION) {
                    value = value / 10D + 1D;
                }
            }
            known = value;
            VALUES.put(item, known);
        }
        return breaksInstantly(item) ? 0D : known;
    }

    private static boolean breaksInstantly(Item item) {
        if (item == Items.AIR) {
            return true;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(BuiltInRegistries.ITEM.getKey(item));
        return block.isPresent() && block.get().defaultDestroyTime() == 0F;
    }
}
