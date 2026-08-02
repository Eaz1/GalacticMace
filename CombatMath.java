package me.eaz.galacticmace;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Vanilla's real armor/toughness/protection formula (verified against the
 * Minecraft Wiki and cross-checked against several independent damage
 * calculators while building this):
 *
 *   armorMitigation = min(20, max(armor/5, armor - 4*damage/(toughness+8))) / 25
 *   afterArmor       = damage * (1 - armorMitigation)
 *   afterProtection  = afterArmor * (1 - min(20, EPF) / 25)
 *
 * This is applied manually (rather than trusting Bukkit's own pre-computed
 * DamageModifier.ARMOR) because Bukkit's armor modifier is calculated
 * against the ORIGINAL vanilla hit and does not rescale when a plugin
 * inflates the base damage afterward - which is exactly what was silently
 * making the Mace ignore armor before. Recomputing the whole formula here
 * against OUR actual raw damage number is what makes armor/Protection
 * apply correctly again.
 */
final class CombatMath {

    private CombatMath() {
    }

    /** Real vanilla per-piece armor points, indexed by material. */
    static double armorPointsFor(Material type) {
        switch (type) {
            case LEATHER_HELMET: return 1;
            case LEATHER_CHESTPLATE: return 3;
            case LEATHER_LEGGINGS: return 2;
            case LEATHER_BOOTS: return 1;
            case CHAINMAIL_HELMET: return 2;
            case CHAINMAIL_CHESTPLATE: return 5;
            case CHAINMAIL_LEGGINGS: return 4;
            case CHAINMAIL_BOOTS: return 1;
            case IRON_HELMET: return 2;
            case IRON_CHESTPLATE: return 6;
            case IRON_LEGGINGS: return 5;
            case IRON_BOOTS: return 2;
            case GOLD_HELMET: return 2;
            case GOLD_CHESTPLATE: return 5;
            case GOLD_LEGGINGS: return 3;
            case GOLD_BOOTS: return 1;
            case DIAMOND_HELMET: return 3;
            case DIAMOND_CHESTPLATE: return 8;
            case DIAMOND_LEGGINGS: return 6;
            case DIAMOND_BOOTS: return 3;
            default: return 0;
        }
    }

    /** Real vanilla per-piece armor toughness - only Diamond (2) has any in 1.12.2; Netherite is handled by NetheriteItems. */
    static double toughnessFor(Material type) {
        switch (type) {
            case DIAMOND_HELMET:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_BOOTS:
                return 2;
            default:
                return 0;
        }
    }

    /** Total armor points across all 4 slots, substituting Netherite's boosted values where recognized. */
    static double totalArmorPoints(LivingEntity entity) {
        double total = 0;
        for (ItemStack piece : armorPieces(entity)) {
            if (piece == null) continue;
            Double netherite = NetheriteItems.armorPointsIfNetherite(piece);
            total += (netherite != null) ? netherite : armorPointsFor(piece.getType());
        }
        return total;
    }

    static double totalToughness(LivingEntity entity) {
        double total = 0;
        for (ItemStack piece : armorPieces(entity)) {
            if (piece == null) continue;
            Double netherite = NetheriteItems.toughnessIfNetherite(piece);
            total += (netherite != null) ? netherite : toughnessFor(piece.getType());
        }
        return total;
    }

    /** Sum of generic Protection levels across worn armor (the only Protection type relevant to plain melee/smash damage). */
    static int totalProtectionEPF(LivingEntity entity) {
        int total = 0;
        for (ItemStack piece : armorPieces(entity)) {
            if (piece == null || !piece.hasItemMeta()) continue;
            if (piece.getItemMeta().hasEnchant(Enchantment.PROTECTION_ENVIRONMENTAL)) {
                total += piece.getItemMeta().getEnchantLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
            }
        }
        return total;
    }

    /** Sum of Blast Protection levels across worn armor (used to soften Wind Burst/Wind Charge knockback, matching vanilla). */
    static int totalBlastProtection(LivingEntity entity) {
        int total = 0;
        for (ItemStack piece : armorPieces(entity)) {
            if (piece == null || !piece.hasItemMeta()) continue;
            if (piece.getItemMeta().hasEnchant(Enchantment.PROTECTION_EXPLOSIONS)) {
                total += piece.getItemMeta().getEnchantLevel(Enchantment.PROTECTION_EXPLOSIONS);
            }
        }
        return total;
    }

    private static ItemStack[] armorPieces(LivingEntity entity) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return new ItemStack[0];
        return eq.getArmorContents();
    }

    /** The full vanilla formula. rawDamage is the fully-assembled pre-armor hit (base + smash bonus + Density, post-crit). */
    static double applyArmorAndProtection(double rawDamage, double armorPoints, double toughness, int epf) {
        if (rawDamage <= 0) return 0;

        double armorMitigation = Math.min(20, Math.max(armorPoints / 5.0, armorPoints - (4 * rawDamage) / (toughness + 8))) / 25.0;
        double afterArmor = rawDamage * (1 - armorMitigation);

        double afterProtection = afterArmor * (1 - Math.min(20, epf) / 25.0);

        return Math.max(0, afterProtection);
    }
}
