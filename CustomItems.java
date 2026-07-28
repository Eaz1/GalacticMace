package com.example.maceplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable; // Spigot 1.12.2 API name may be ItemMeta only; see note below

import java.util.Arrays;

/**
 * Builds the custom items. Model selection in 1.12.2 has no CustomModelData,
 * so we rely on two tricks the resource pack's item JSON depends on:
 *
 *  MACE  -> base item DIAMOND_HOE, set Unbreakable + a fixed near-max
 *           durability value so the client's "damage" predicate (>= 0.99)
 *           in models/item/diamond_hoe.json picks models/item/mace.json.
 *           A normal, legitimately-worn diamond hoe would need to be at
 *           ~99% durability lost to visually collide with this - script
 *           you may also just remove diamond hoes from your server's
 *           loot/crafting to make the id fully "yours".
 *
 *  WIND CHARGE -> base item SNOWBALL. The resource pack fully re-skins
 *           snowball.json, so ALL snowballs on the server will show the
 *           Wind Charge texture. If you need real snowballs too, swap
 *           the base item to EGG or another throwable you don't otherwise use.
 */
public final class CustomItems {

    public static final String MACE_TAG = "custom_mace";
    public static final String WIND_CHARGE_TAG = "custom_wind_charge";

    private CustomItems() {}

    public static ItemStack createMace() {
        ItemStack item = new ItemStack(Material.DIAMOND_HOE, 1);
        short maxDurability = item.getType().getMaxDurability(); // 1561 in 1.12.2
        short fakeDamage = (short) Math.floor(maxDurability * 0.995); // lands in the 0.99+ predicate bucket
        item.setDurability(fakeDamage);

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Mace");
        meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "A heavy, blunt weapon."));
        meta.setUnbreakable(true); // keeps the fake damage value from ever changing
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE,
                           org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        // Tag it with a hidden persistent marker so plugin logic can
        // identify "this exact item is a Mace" regardless of texture load.
        // 1.12.2 has no PersistentDataContainer (that's 1.14+), so use
        // NBTTagCompound via NMS or a lightweight NBT-API library, e.g.:
        //   NBTItem nbtItem = new NBTItem(item);
        //   nbtItem.setBoolean(MACE_TAG, true);
        //   item = nbtItem.getItem();
        return item;
    }

    public static ItemStack createWindCharge() {
        ItemStack item = new ItemStack(Material.SNOW_BALL, 1); // SNOW_BALL is the 1.12.2 Bukkit enum name
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Wind Charge");
        meta.setLore(Arrays.asList(ChatColor.DARK_GRAY + "Throw to launch yourself and nearby entities."));
        item.setItemMeta(meta);
        // Same NBT-tag note as above applies here.
        return item;
    }

    public static boolean isMace(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_HOE || !item.hasItemMeta()) return false;
        return item.getItemMeta().isUnbreakable();
        // Prefer the NBT tag check in production; durability/unbreakable
        // check is a reasonable stand-in shown here for brevity.
    }

    public static boolean isWindCharge(ItemStack item) {
        if (item == null || item.getType() != Material.SNOW_BALL || !item.hasItemMeta()) return false;
        return ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("Wind Charge");
    }
}
