package me.eaz.galacticmace;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shape (3x3, crafting table required):
 *   E W E
 *   W D W
 *   E W E
 * E = Elytra, W = Wind Charge, D = Dragon Egg
 *
 * The recipe itself can only require plain Material.SNOW_BALL in the W
 * slots - 1.12.2's recipe API has no NBT/meta-aware ingredient matching
 * (that arrived with RecipeChoice in 1.13). PrepareItemCraftEvent closes
 * that gap by checking the actual items in those 4 slots and blanking the
 * result if any of them isn't a real, tagged Wind Charge.
 */
public class MaceRecipe implements Listener {

    private static final int[] WIND_CHARGE_SLOTS = {1, 3, 5, 7};

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public MaceRecipe(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "mace");
    }

    public void register() {
        ItemStack result = CustomItems.createMace();

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("EWE", "WDW", "EWE");
        recipe.setIngredient('E', Material.ELYTRA);
        recipe.setIngredient('W', Material.SNOW_BALL);
        recipe.setIngredient('D', Material.DRAGON_EGG);

        plugin.getServer().addRecipe(recipe);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof ShapedRecipe)) return;
        if (!key.equals(((ShapedRecipe) recipe).getKey())) return;

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        for (int slot : WIND_CHARGE_SLOTS) {
            if (slot >= matrix.length || !CustomItems.isWindCharge(matrix[slot])) {
                inv.setResult(null);
                return;
            }
        }
    }
}
