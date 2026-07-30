package me.eaz.galacticmace;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public final class CustomItems {

    public static final String MACE_TAG = "custom_mace";
    public static final String WIND_CHARGE_TAG = "custom_wind_charge";

    private CustomItems() {
    }

    public static ItemStack createMace() {
        ItemStack item = new ItemStack(Material.DIAMOND_HOE, 1);

        short maxDurability = item.getType().getMaxDurability();
        short fakeDamage = (short) (maxDurability * 0.995);
        item.setDurability(fakeDamage);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GRAY + "Mace");
        meta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "A heavy, blunt weapon."
        ));

        meta.setUnbreakable(true);
        meta.addItemFlags(
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ATTRIBUTES
        );

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createWindCharge() {
        ItemStack item = new ItemStack(Material.SNOW_BALL, 1);

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Wind Charge");
        meta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "Throw to launch yourself and nearby entities."
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Identifies a Mace by type + Unbreakable + the fixed fake-durability
     * value (see createMace()), NOT by display name. Anvils let players
     * rename items, and a renamed Mace must still be recognized as a Mace
     * everywhere else in the plugin (anvil merging, damage bonuses, etc).
     */
    public static boolean isMace(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.DIAMOND_HOE) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().isUnbreakable()) return false;

        short max = item.getType().getMaxDurability();
        short expected = (short) (max * 0.995);
        return Math.abs(item.getDurability() - expected) <= 1;
    }

    public static boolean isWindCharge(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.SNOW_BALL) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();

        return meta.hasDisplayName()
                && ChatColor.stripColor(meta.getDisplayName()).equals("Wind Charge");
    }
}
