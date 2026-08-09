package me.eaz.galacticmace;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class NetheriteItems {
    static final String ARMOR_MARKER = ChatColor.DARK_GRAY + "Forged from ancient debris.";
    static final String TOOL_MARKER = ChatColor.DARK_GRAY + "Forged from ancient debris.";
    static final double TOOL_PREDICATE_DAMAGE = 1.0;
    private final JavaPlugin plugin;

    public NetheriteItems(JavaPlugin plugin) { this.plugin = plugin; }

    public ItemStack createHelmet() { return armorPiece(Material.CHAINMAIL_HELMET, "Netherite Helmet", "head", cfg("helmet.armor", 4), (int) cfg("helmet.durability", 1024)); }
    public ItemStack createChestplate() { return armorPiece(Material.CHAINMAIL_CHESTPLATE, "Netherite Chestplate", "chest", cfg("chestplate.armor", 9), (int) cfg("chestplate.durability", 1024)); }
    public ItemStack createLeggings() { return armorPiece(Material.CHAINMAIL_LEGGINGS, "Netherite Leggings", "legs", cfg("leggings.armor", 7), (int) cfg("leggings.durability", 1024)); }
    public ItemStack createBoots() { return armorPiece(Material.CHAINMAIL_BOOTS, "Netherite Boots", "feet", cfg("boots.armor", 4), (int) cfg("boots.durability", 1024)); }

    private ItemStack armorPiece(Material base, String name, String slot, double armorPoints, int durability) {
        // The server-side durability is custom, but the 1.12.2 client only
        // knows the vanilla chainmail max. Put the item at the client max as
        // the visual marker; the predicate stays selected as the real item
        // wears further because the client-side damage value is clamped.
        short clientMax = base.getMaxDurability();
        NMSUtil.setMaxDurability(plugin, base, durability);

        ItemStack item = new ItemStack(base, 1);
        item.setDurability(clientMax);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + name);
        meta.setLore(Arrays.asList(ARMOR_MARKER));
        item.setItemMeta(meta);

        double toughness = plugin.getConfig().getDouble("netherite.toughness-per-piece", 3.0);
        double knockbackResist = plugin.getConfig().getDouble("netherite.knockback-resistance-per-piece", 0.1);
        double armorDelta = armorPoints - CombatMath.armorPointsFor(base);
        item = NMSUtil.addAttribute(plugin, item, "generic.armor", armorDelta, slot);
        item = NMSUtil.addAttribute(plugin, item, "generic.armorToughness", toughness, slot);
        item = NMSUtil.addAttribute(plugin, item, "generic.knockbackResistance", knockbackResist, slot);
        return item;
    }

    public static boolean isNetheriteArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Material t = item.getType();
        boolean chainmail = t == Material.CHAINMAIL_HELMET || t == Material.CHAINMAIL_CHESTPLATE
                || t == Material.CHAINMAIL_LEGGINGS || t == Material.CHAINMAIL_BOOTS;
        if (!chainmail) return false;
        List<String> lore = item.getItemMeta().getLore();
        return lore != null && lore.contains(ARMOR_MARKER);
    }

    static Double armorPointsIfNetherite(ItemStack item) {
        if (!isNetheriteArmor(item)) return null;
        JavaPlugin p = GalacticMace.getInstance();
        switch (item.getType()) {
            case CHAINMAIL_HELMET: return (double) p.getConfig().getInt("netherite.helmet.armor", 4);
            case CHAINMAIL_CHESTPLATE: return (double) p.getConfig().getInt("netherite.chestplate.armor", 9);
            case CHAINMAIL_LEGGINGS: return (double) p.getConfig().getInt("netherite.leggings.armor", 7);
            case CHAINMAIL_BOOTS: return (double) p.getConfig().getInt("netherite.boots.armor", 4);
            default: return null;
        }
    }

    static Double toughnessIfNetherite(ItemStack item) {
        if (!isNetheriteArmor(item)) return null;
        return GalacticMace.getInstance().getConfig().getDouble("netherite.toughness-per-piece", 3.0);
    }

    public ItemStack createSword() { return tool(Material.DIAMOND_SWORD, "Netherite Sword", "mainhand", cfg("sword.attack-damage", 8.0), (int) cfg("sword.durability", 2048)); }
    public ItemStack createPickaxe() { return tool(Material.DIAMOND_PICKAXE, "Netherite Pickaxe", "mainhand", cfg("pickaxe.attack-damage", 6.0), (int) cfg("pickaxe.durability", 2048)); }
    public ItemStack createAxe() { return tool(Material.DIAMOND_AXE, "Netherite Axe", "mainhand", cfg("axe.attack-damage", 10.0), (int) cfg("axe.durability", 2048)); }
    public ItemStack createShovel() { return tool(Material.DIAMOND_SPADE, "Netherite Shovel", "mainhand", cfg("shovel.attack-damage", 6.5), (int) cfg("shovel.durability", 2048)); }
    public ItemStack createHoe() { return tool(Material.DIAMOND_HOE, "Netherite Hoe", "mainhand", null, (int) cfg("hoe.durability", 2048)); }

    private ItemStack tool(Material base, String name, String slot, Double attackDamage, int durability) {
        short clientMax = base.getMaxDurability();
        NMSUtil.setMaxDurability(plugin, base, durability);

        ItemStack item = new ItemStack(base, 1);
        item.setDurability(clientMax);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + name);
        meta.setLore(Arrays.asList(TOOL_MARKER));
        item.setItemMeta(meta);

        if (attackDamage != null) {
            double diamondBase = diamondAttackDamage(base);
            item = NMSUtil.addAttribute(plugin, item, "generic.attackDamage", attackDamage - diamondBase, slot);
        }
        return item;
    }

    private double diamondAttackDamage(Material base) {
        switch (base) {
            case DIAMOND_SWORD: return 7.0;
            case DIAMOND_PICKAXE: return 5.0;
            case DIAMOND_AXE: return 9.0;
            case DIAMOND_SPADE: return 5.5;
            default: return 0.0;
        }
    }

    public static boolean isNetheriteTool(ItemStack item, Material expectedBase, double ignored) {
        if (item == null || item.getType() != expectedBase || !item.hasItemMeta()) return false;
        List<String> lore = item.getItemMeta().getLore();
        return lore != null && lore.contains(TOOL_MARKER);
    }

    public static boolean isAnyNetheriteTool(ItemStack item) {
        if (item == null) return false;
        return isNetheriteTool(item, Material.DIAMOND_SWORD, TOOL_PREDICATE_DAMAGE)
                || isNetheriteTool(item, Material.DIAMOND_PICKAXE, TOOL_PREDICATE_DAMAGE)
                || isNetheriteTool(item, Material.DIAMOND_AXE, TOOL_PREDICATE_DAMAGE)
                || isNetheriteTool(item, Material.DIAMOND_SPADE, TOOL_PREDICATE_DAMAGE)
                || isNetheriteTool(item, Material.DIAMOND_HOE, TOOL_PREDICATE_DAMAGE);
    }

    private double cfg(String path, double def) { return plugin.getConfig().getDouble("netherite." + path, def); }
}
