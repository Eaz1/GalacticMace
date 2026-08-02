package me.eaz.galacticmace;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every crafting recipe lives in config.yml under "recipes" rather than
 * hardcoded Java, so a server owner can add/edit/remove recipes (Mace,
 * Wind Charge, or anything added later) without recompiling.
 *
 * Example config.yml shape:
 *
 *   recipes:
 *     mace:
 *       enabled: true
 *       type: shaped
 *       result: mace          # "mace" / "wind_charge" resolve to our custom
 *       shape:                # items; anything else is parsed as a Material.
 *         - "EWE"
 *         - "WDW"
 *         - "EWE"
 *       ingredients:
 *         E: ELYTRA
 *         W: wind_charge
 *         D: DRAGON_EGG
 *     wind_charge:
 *       enabled: true
 *       type: shapeless
 *       result: wind_charge
 *       result-amount: 8
 *       ingredients: [ENDER_PEARL, SNOWBALL]
 *
 * 1.12.2's recipe API can't check item NBT/Lore as an ingredient (that
 * arrived with RecipeChoice in 1.13), so any shaped-recipe slot whose
 * ingredient is one of our custom item keys ("mace", "wind_charge") gets
 * an extra PrepareItemCraftEvent check to stop a plain vanilla item from
 * substituting for it. The Netherite recipes don't need that same check
 * for their own ingredients (Diamond gear, Sticks are all plain,
 * unambiguous vanilla materials) - but DO need the reverse check, since
 * those same Diamond/Chainmail materials are also what this plugin's
 * OWN custom items are built on (see SHARED_MATERIALS). To make a
 * future custom item usable as an ambiguous ingredient, add it to
 * isCustomKey() and isValidIngredient() below; resolveResult() /
 * resolveIngredientMaterial() already show the pattern for adding any
 * new result type.
 *
 * Disabling a recipe (enabled: false) means it's simply never registered
 * in the first place - confirmed while building this that 1.12.2's API
 * has no Server#removeRecipe(NamespacedKey) at all (that method doesn't
 * exist before 1.16.2 in any javadoc found for this project, only a full
 * Server#resetRecipes() wiping literally everything). So: disabling a
 * recipe that was NEVER previously enabled works immediately. Flipping
 * an already-enabled recipe to disabled needs a full server restart to
 * take effect, not just /reload - a /reload doesn't clear previously
 * registered recipes, and this plugin has no way to selectively remove
 * just one.
 */
public class RecipeLoader implements Listener {

    /**
     * Every vanilla material this plugin also uses as the base for a
     * tagged custom item. Any shaped-recipe slot whose ingredient
     * resolves to one of these gets an automatic check that the actual
     * item is a genuinely plain, untagged instance - otherwise a player
     * could feed a crafted Netherite Sword back in as "a diamond sword"
     * ingredient (they share Material.DIAMOND_SWORD), or use a Mace
     * (Material.DIAMOND_AXE) to craft a Netherite Axe. This check
     * applies automatically based on the resolved Material, not on how
     * the admin phrased the ingredient in config.yml.
     */
    private static final Set<Material> SHARED_MATERIALS = EnumSet.of(
            Material.DIAMOND_AXE, Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE,
            Material.DIAMOND_SPADE, Material.DIAMOND_HOE,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.SNOW_BALL
    );

    private final JavaPlugin plugin;
    private final NetheriteItems netheriteItems;

    /** recipe key -> (matrix slot -> configured ingredient key), for any shaped-recipe slot that needs the extra check above. */
    private final Map<NamespacedKey, Map<Integer, String>> customSlotChecks = new HashMap<>();

    public RecipeLoader(JavaPlugin plugin, NetheriteItems netheriteItems) {
        this.plugin = plugin;
        this.netheriteItems = netheriteItems;
    }

    public void loadAll() {
        ConfigurationSection recipes = plugin.getConfig().getConfigurationSection("recipes");
        if (recipes == null) {
            plugin.getLogger().warning("[RecipeLoader] No 'recipes' section in config.yml - no recipes registered.");
            return;
        }

        for (String id : recipes.getKeys(false)) {
            try {
                loadOne(id, recipes.getConfigurationSection(id));
            } catch (Exception ex) {
                plugin.getLogger().warning("[RecipeLoader] Skipping invalid recipe '" + id + "': " + ex.getMessage());
            }
        }
    }

    private void loadOne(String id, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) return;

        String type = section.getString("type", "shaped");
        String resultKey = section.getString("result");
        if (resultKey == null || resultKey.trim().isEmpty()) {
            throw new IllegalStateException("missing 'result'");
        }
        int amount = Math.max(1, section.getInt("result-amount", 1));
        ItemStack result = resolveResult(resultKey, amount);
        NamespacedKey key = new NamespacedKey(plugin, id);

        if (type.equalsIgnoreCase("shaped")) {
            loadShaped(key, section, result);
        } else if (type.equalsIgnoreCase("shapeless")) {
            loadShapeless(key, section, result);
        } else {
            throw new IllegalStateException("unknown recipe type '" + type + "' (expected 'shaped' or 'shapeless')");
        }
    }

    private void loadShaped(NamespacedKey key, ConfigurationSection section, ItemStack result) {
        List<String> shapeLines = section.getStringList("shape");
        if (shapeLines.isEmpty() || shapeLines.size() > 3) {
            throw new IllegalStateException("'shape' must have 1-3 rows");
        }
        int width = shapeLines.get(0).length();
        for (String line : shapeLines) {
            if (line.length() > 3) throw new IllegalStateException("each shape row must be at most 3 characters");
            if (line.length() != width) throw new IllegalStateException("all shape rows must be the same length (pad with spaces for empty slots)");
        }

        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients == null) throw new IllegalStateException("missing 'ingredients'");

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shapeLines.toArray(new String[0]));

        Map<Integer, String> customSlots = new HashMap<>();
        for (String symbolStr : ingredients.getKeys(false)) {
            char symbol = symbolStr.charAt(0);
            String ingredientKey = ingredients.getString(symbolStr);
            if (ingredientKey == null) continue;

            Material material = resolveIngredientMaterial(ingredientKey);
            recipe.setIngredient(symbol, material);

            boolean needsCheck = isCustomKey(ingredientKey) || SHARED_MATERIALS.contains(material);
            if (needsCheck) {
                for (int row = 0; row < shapeLines.size(); row++) {
                    String line = shapeLines.get(row);
                    for (int col = 0; col < line.length(); col++) {
                        if (line.charAt(col) == symbol) {
                            customSlots.put(row * 3 + col, ingredientKey);
                        }
                    }
                }
            }
        }

        plugin.getServer().addRecipe(recipe);
        if (!customSlots.isEmpty()) {
            customSlotChecks.put(key, customSlots);
        }
    }

    private void loadShapeless(NamespacedKey key, ConfigurationSection section, ItemStack result) {
        List<String> ingredientKeys = section.getStringList("ingredients");
        if (ingredientKeys.isEmpty()) throw new IllegalStateException("missing 'ingredients'");

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (String ingredientKey : ingredientKeys) {
            recipe.addIngredient(1, resolveIngredientMaterial(ingredientKey));
        }

        plugin.getServer().addRecipe(recipe);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        NamespacedKey key = keyOf(event.getRecipe());
        if (key == null) return;

        Map<Integer, String> checks = customSlotChecks.get(key);
        if (checks == null) return;

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        for (Map.Entry<Integer, String> entry : checks.entrySet()) {
            int slot = entry.getKey();
            if (slot >= matrix.length || !isValidIngredient(matrix[slot], entry.getValue())) {
                inv.setResult(null);
                return;
            }
        }
    }

    private NamespacedKey keyOf(Recipe recipe) {
        if (recipe instanceof ShapedRecipe) return ((ShapedRecipe) recipe).getKey();
        if (recipe instanceof ShapelessRecipe) return ((ShapelessRecipe) recipe).getKey();
        return null;
    }

    /**
     * Two directions of check, both driven by the same configured
     * ingredient key:
     *   - configured as "mace"/"wind_charge" -> the slot must actually
     *     BE that tagged item (blocks a plain vanilla substitute).
     *   - configured as a plain material that happens to be one this
     *     plugin also tags for a custom item -> the slot must NOT be
     *     any of those tagged items (blocks feeding a crafted custom
     *     item back in as "plain" raw material).
     */
    private boolean isValidIngredient(ItemStack item, String configuredKey) {
        if (configuredKey.equalsIgnoreCase("wind_charge")) return CustomItems.isWindCharge(item);
        if (configuredKey.equalsIgnoreCase("mace")) return CustomItems.isMace(item);

        if (item == null) return false;
        return !CustomItems.isMace(item) && !NetheriteItems.isAnyNetheriteTool(item) && !NetheriteItems.isNetheriteArmor(item);
    }

    private boolean isCustomKey(String key) {
        return key.equalsIgnoreCase("mace") || key.equalsIgnoreCase("wind_charge");
    }

    private ItemStack resolveResult(String key, int amount) {
        ItemStack item;
        switch (key.toLowerCase()) {
            case "mace": item = CustomItems.createMace(); break;
            case "wind_charge": item = CustomItems.createWindCharge(); break;
            case "netherite_helmet": item = netheriteItems.createHelmet(); break;
            case "netherite_chestplate": item = netheriteItems.createChestplate(); break;
            case "netherite_leggings": item = netheriteItems.createLeggings(); break;
            case "netherite_boots": item = netheriteItems.createBoots(); break;
            case "netherite_sword": item = netheriteItems.createSword(); break;
            case "netherite_pickaxe": item = netheriteItems.createPickaxe(); break;
            case "netherite_axe": item = netheriteItems.createAxe(); break;
            case "netherite_shovel": item = netheriteItems.createShovel(); break;
            case "netherite_hoe": item = netheriteItems.createHoe(); break;
            default: item = new ItemStack(Material.valueOf(key.toUpperCase()));
        }
        item.setAmount(amount);
        return item;
    }

    private Material resolveIngredientMaterial(String key) {
        if (key.equalsIgnoreCase("mace")) return Material.DIAMOND_AXE;
        if (key.equalsIgnoreCase("wind_charge")) return Material.SNOW_BALL;
        return Material.valueOf(key.toUpperCase());
    }
}
