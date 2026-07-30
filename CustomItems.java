package me.eaz.galacticmace;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class CustomItems {

    /** First Lore line on every Mace - also used as an identity marker, see isMace(). */
    public static final String MACE_MARKER = ChatColor.DARK_GRAY + "A heavy, blunt weapon.";

    private CustomItems() {
    }

    public static ItemStack createMace() {
        ItemStack item = new ItemStack(Material.DIAMOND_AXE, 1);

        // Fixed fake-durability value the resource pack's diamond_axe.json
        // "damage" predicate keys off of (see MaceCombatListener, which
        // cancels PlayerItemDamageEvent for maces so this never drifts).
        //
        // IMPORTANT: this item is deliberately NOT set Unbreakable. Vanilla's
        // "damage" model-override predicate does not reliably fire on
        // Unbreakable items (confirmed on the Minecraft Wiki and in multiple
        // bug reports) - combining the two silently breaks the texture, which
        // is exactly what caused the Mace to render as a plain axe before.
        short maxDurability = item.getType().getMaxDurability();
        short fakeDamage = (short) (maxDurability * 0.995);
        item.setDurability(fakeDamage);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GRAY + "Mace");
        meta.setLore(Arrays.asList(MACE_MARKER));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

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
     * Identifies a Mace by type + the fixed fake-durability value + the
     * marker Lore line, NOT by display name (anvils let players rename
     * items, and a renamed Mace must still be recognized everywhere else
     * in the plugin - combat, anvil merging, the crafting recipe, etc).
     *
     * The Lore-line check exists because a legitimately-crafted vanilla
     * diamond axe COULD, in extremely rare cases, happen to sit at the same
     * ~99.5%-used durability we use as our texture marker; requiring the
     * Lore line too makes that coincidence harmless for every piece of
     * plugin logic that gates on isMace(), even though the texture itself
     * (a client-side, NBT-only decision) can't be helped either way.
     */
    public static boolean isMace(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.DIAMOND_AXE) return false;
        if (!item.hasItemMeta()) return false;

        short max = item.getType().getMaxDurability();
        short expected = (short) (max * 0.995);
        if (Math.abs(item.getDurability() - expected) > 1) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        return lore.contains(MACE_MARKER);
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
