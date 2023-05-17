package com.volmit.react.util.value;

import art.arcane.chrono.PrecisionStopwatch;
import com.google.gson.Gson;
import com.volmit.react.React;
import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.io.IO;
import com.volmit.react.util.json.JSONObject;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Getter
public class MaterialValue {
    private static final Map<Material, Double> valueMultipliers = new HashMap<>();
    private static MaterialValue valueCache = null;

    static {
        ReactConfiguration.get().getValue().getValueMutlipliers().forEach((k, v) -> {
            try {
                Material m = Material.valueOf(k.toUpperCase());
                valueMultipliers.put(m, v);
            } catch (Exception e) {
                React.verbose("Invalid material value multiplier: " + k);
            }
        });
    }

    private final Map<Material, Double> value = new HashMap<>();

    public static void save() {
        if (valueCache == null) {
            return;
        }

        File l = React.instance.getDataFile("data", "value-cache.json");
        try {
            IO.writeAll(l, new JSONObject(new Gson().toJson(valueCache)).toString(4));
        } catch (IOException e) {
            React.verbose("Failed to save value cache");
        }
    }

    public static MaterialValue get() {
        if (valueCache == null) {
            MaterialValue dummy = new MaterialValue();
            File l = React.instance.getDataFile("data", "value-cache.json");

            if (!l.exists()) {
                try {
                    IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                } catch (IOException e) {
                    e.printStackTrace();
                    valueCache = dummy;
                    return dummy;
                }
            }

            try {
                valueCache = new Gson().fromJson(IO.readAll(l), MaterialValue.class);
            } catch (IOException e) {
                e.printStackTrace();
                valueCache = new MaterialValue();
            }
        }

        return valueCache;
    }

    public static void debugValue(Material m) {
        debugValue(m, 0, 1, new HashSet<>());
    }

    private static void debugValue(Material m, int ind, int x, Set<MaterialRecipe> ignore) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        React.verbose(Form.repeat("  ", ind) + m.name() + ": " + getValue(m) + (x == 1 ? "" : " (x" + x + ")"));

        int r = 0;
        for (MaterialRecipe i : getRecipes(m)) {
            if (ignore.contains(i)) {
                continue;
            }

            ignore.add(i);
            if (ignore.size() > ReactConfiguration.get().getValue().getMaxRecipeListPrecaution()) {
                React.verbose("Avoiding infinite loop on " + m.name() + " from " + i.toString());
                return;
            }

            int o = i.getOutput().getAmount();
            React.verbose(Form.repeat("  ", ind) + "# Recipe [" + ind + "x" + r + (o == 1 ? "]" : "] (x" + o + ") "));

            for (MaterialCount j : i.getInput()) {
                debugValue(j.getMaterial(), ind + 1, j.getAmount(), ignore);
            }

            r++;
        }
        React.verbose(Form.repeat("  ", ind) + " took " + Form.duration(p.getMilliseconds(), 0));
    }

    private static double getMultiplier(Material m) {
        Double d = ReactConfiguration.get().getValue().getValueMutlipliers().get(m);
        return d == null ? 1 : d;
    }

    public static double getValue(Material m) {
        try {
            return getValue(m, new HashSet<>());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static double getValue(Material m, Set<MaterialRecipe> ignore) {
        if (get().value.containsKey(m)) {
            if (m.isBlock() && m.getHardness() == 0) {
                return 0;
            }
            return get().value.get(m);
        }
        double v = ReactConfiguration.get().getValue().getBaseValue();
        List<MaterialRecipe> recipes = getRecipes(m);
        if (recipes.isEmpty()) {
            get().value.put(m, v * getMultiplier(m)); // No recipes, just use base value, if no base value then 1
        } else {
            List<Double> d = new ArrayList<>();
            for (MaterialRecipe i : recipes) {
                if (ignore.contains(i)) {
                    continue;
                }
                ignore.add(i);
                double vx = v;
                for (MaterialCount j : i.getInput()) {
                    vx += getValue(j.getMaterial(), ignore);
                }
                d.add(vx / i.getOutput().getAmount());
            }
            if (d.size() > 0) {
                v += d.stream().mapToDouble(i -> i).average().getAsDouble();
            }
            if (v > ReactConfiguration.get().getValue().getMaxRecipeListPrecaution()) {
                get().value.put(m, (v / 10 + 1) * getMultiplier(m));
            } else {
                get().value.put(m, v);
            }

        }
        if (m.isBlock() && m.getHardness() == 0) {
            return 0;
        }
        return get().value.get(m);
    }

    private static List<MaterialRecipe> getRecipes(Material mat) {
        List<MaterialRecipe> r = new ArrayList<>();
        try {
            ItemStack is = new ItemStack(mat);
            try {
                is.setDurability((short) -1);
            } catch (Throwable e) {
                React.verbose("Failed to set durability of " + mat.name());
            }
            Bukkit.getRecipesFor(is).forEach(i -> {
                MaterialRecipe rx = toMaterial(i);
                if (rx != null) {
                    r.add(rx);
                }
            });
        } catch (Throwable e) {
            React.verbose("Failed to get recipes for " + mat.name());
        }
        return r;
    }

    private static MaterialRecipe toMaterial(Recipe r) {
        try {
            if (r instanceof ShapelessRecipe recipe) {
                return MaterialRecipe.builder()
                        .input(new ArrayList<>(recipe.getIngredientList().stream().map(i -> new MaterialCount(i.getType(), 1)).toList()))
                        .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                        .build();
            } else if (r instanceof ShapedRecipe recipe) {
                MaterialRecipe re = MaterialRecipe.builder()
                        .input(new ArrayList<>())
                        .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                        .build();
                Map<Material, Integer> f = new HashMap<>();
                for (ItemStack i : recipe.getIngredientMap().values()) {
                    if (i == null || i.getType().isAir()) {
                        continue;
                    }

                    f.compute(i.getType(), (k, v) -> v == null ? 1 : v + 1);
                }

                f.forEach((k, v) -> re.getInput().add(new MaterialCount(k, v)));

                return re;
            } else if (r instanceof CookingRecipe recipe) {
                List<MaterialCount> a = new ArrayList<>();
                a.add(new MaterialCount(recipe.getInput().getType(), 1));

                return MaterialRecipe.builder()
                        .input(a)
                        .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                        .build();
            } else if (r instanceof MerchantRecipe recipe) {
                return MaterialRecipe.builder()
                        .input(new ArrayList<>(recipe.getIngredients().stream().map(i -> new MaterialCount(i.getType(), 1)).toList()))
                        .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                        .build();
            } else if (r instanceof StonecuttingRecipe recipe) {
                List<MaterialCount> a = new ArrayList<>();
                a.add(new MaterialCount(recipe.getInput().getType(), 1));

                return MaterialRecipe.builder()
                        .input(a)
                        .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                        .build();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }
}
