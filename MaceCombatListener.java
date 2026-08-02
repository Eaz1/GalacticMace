package me.eaz.galacticmace;

import me.eaz.galacticmace.enchant.EnchantUtil;
import me.eaz.galacticmace.enchant.MaceEnchant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real combat behavior for the Mace, matching vanilla 1.21 as closely as
 * 1.12.2's API allows. All numbers are configurable in config.yml under
 * items.mace and enchantments.*.
 *
 * Damage is computed fully by hand (see CombatMath) rather than through
 * Bukkit's DamageModifier system, because that system doesn't rescale
 * ARMOR/MAGIC when a plugin inflates BASE afterward - which is exactly
 * what was letting the Mace ignore armor and Protection before.
 */
public class MaceCombatListener implements Listener {

    private final JavaPlugin plugin;
    private final FallImmunity fallImmunity;

    /** Per-player record of the last hit's weapon + its own pre-armor raw damage, for the attribute-swap window. */
    private final Map<UUID, HitRecord> lastHit = new HashMap<>();

    public MaceCombatListener(JavaPlugin plugin, FallImmunity fallImmunity) {
        this.plugin = plugin;
        this.fallImmunity = fallImmunity;
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (CustomItems.isMace(event.getItem()) || NetheriteItems.isAnyNetheriteTool(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();

        HitRecord previous = lastHit.get(uuid);

        if (!CustomItems.isMace(weapon)) {
            // Not the Mace - vanilla handles this hit entirely. Just remember
            // it (weapon type + vanilla's own pre-armor raw damage) in case
            // the very next hit swaps to the Mace within the attribute-swap window.
            double rawBase = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            lastHit.put(uuid, new HitRecord(weapon.getType(), rawBase, now));
            return;
        }

        LivingEntity target = (LivingEntity) event.getEntity();

        double baseDamage = plugin.getConfig().getDouble("mace.base-damage", 6.0);
        double critMultiplier = plugin.getConfig().getDouble("mace.smash-crit-multiplier", 1.5);
        float fall = attacker.getFallDistance();
        boolean smash = fall > 1.5f;

        double rawDamage;
        boolean swapWindow = previous != null
                && (now - previous.timestamp) < 1000
                && previous.weaponType != weapon.getType();

        if (swapWindow) {
            // Real vanilla's attack-attribute lag: within ~1s of hitting with a
            // DIFFERENT weapon, this hit's raw damage number is that previous
            // weapon's, while the currently-held Mace's own enchant effects
            // (Breach/Density/Wind Burst below) still apply on top.
            rawDamage = previous.rawDamage;
        } else if (smash) {
            double bonus = smashTierBonus(fall);

            int densityLevel = EnchantUtil.getLevel(weapon, MaceEnchant.DENSITY);
            if (densityLevel > 0) {
                double perLevel = plugin.getConfig().getDouble("enchantments.density.damage-per-level", 0.5);
                bonus += densityLevel * perLevel * fall;
            }

            rawDamage = (baseDamage + bonus) * critMultiplier;
            fallImmunity.grant(attacker);
            attacker.setFallDistance(0f);
        } else {
            rawDamage = baseDamage;
        }

        double armorPoints = CombatMath.totalArmorPoints(target);
        double toughness = CombatMath.totalToughness(target);
        int epf = CombatMath.totalProtectionEPF(target);

        int breachLevel = EnchantUtil.getLevel(weapon, MaceEnchant.BREACH);
        if (breachLevel > 0) {
            double ignorePerLevel = plugin.getConfig().getDouble("enchantments.breach.armor-ignore-per-level", 0.15);
            double keepFraction = Math.max(0.0, 1.0 - (breachLevel * ignorePerLevel));
            armorPoints *= keepFraction;
            toughness *= keepFraction;
        }

        double finalDamage = CombatMath.applyArmorAndProtection(rawDamage, armorPoints, toughness, epf);

        // Zero every modifier Bukkit pre-computed for the ORIGINAL vanilla hit
        // (it was calculated against a completely different raw number) and
        // put our fully-resolved final damage straight into BASE, so the
        // event's total is exactly our number with no double-application.
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.ARMOR);
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.MAGIC);
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.RESISTANCE);
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage);

        lastHit.put(uuid, new HitRecord(weapon.getType(), rawDamage, now));

        if (smash) {
            knockback(attacker, target);

            int windBurstLevel = EnchantUtil.getLevel(weapon, MaceEnchant.WIND_BURST);
            if (windBurstLevel > 0) {
                double reduction = 1.0 - Math.min(1.0, CombatMath.totalBlastProtection(attacker) * 0.15);
                WindChargeMechanics.launchUpward(attacker, windBurstLevel, reduction);
                fallImmunity.grant(attacker);
                WindChargeMechanics.pushNearby(plugin, attacker.getLocation());
            }
        }
    }

    private void zeroModifierIfApplicable(EntityDamageByEntityEvent event, EntityDamageEvent.DamageModifier modifier) {
        if (event.isApplicable(modifier)) {
            event.setDamage(modifier, 0.0);
        }
    }

    private double smashTierBonus(double fallBlocks) {
        double remaining = fallBlocks;
        double bonus = 0;

        double tier1 = Math.min(remaining, 3);
        bonus += tier1 * 4;
        remaining -= tier1;

        if (remaining > 0) {
            double tier2 = Math.min(remaining, 5);
            bonus += tier2 * 2;
            remaining -= tier2;
        }

        if (remaining > 0) {
            bonus += remaining * 1;
        }

        return bonus;
    }

    private void knockback(Player attacker, LivingEntity target) {
        double strength = plugin.getConfig().getDouble("mace.knockback-strength", 1.2);
        Vector dir = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (dir.lengthSquared() < 0.0001) {
            dir = new Vector(0, 0, 1);
        }
        dir.setY(0);
        if (dir.lengthSquared() > 0.0001) {
            dir.normalize();
        }
        Vector push = dir.multiply(strength);
        push.setY(0.4);
        target.setVelocity(target.getVelocity().add(push));
    }

    private static final class HitRecord {
        final org.bukkit.Material weaponType;
        final double rawDamage;
        final long timestamp;

        HitRecord(org.bukkit.Material weaponType, double rawDamage, long timestamp) {
            this.weaponType = weaponType;
            this.rawDamage = rawDamage;
            this.timestamp = timestamp;
        }
    }
}
