package me.eaz.galacticmace;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** 1 Ender Pearl + 1 Snowball -> 8 Wind Charges. Both ingredients are plain vanilla materials, so no extra validation is needed here (unlike the Mace recipe, nothing ambiguous is being consumed). */
public class WindChargeRecipe {

    private final JavaPlugin plugin;

    public WindChargeRecipe(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        ItemStack result = CustomItems.createWindCharge();
        result.setAmount(8);

        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "wind_charge"), result);
        recipe.addIngredient(1, Material.ENDER_PEARL);
        recipe.addIngredient(1, Material.SNOW_BALL);

        plugin.getServer().addRecipe(recipe);
    }
}
