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

/**
 * Real combat behavior for the Mace, approximating vanilla 1.21's mace as
 * closely as reasonably possible within Bukkit 1.12.2's older damage-event
 * API. All numbers are configurable in config.yml under items.mace and
 * enchantments.*.
 */
public class MaceCombatListener implements Listener {

    private final JavaPlugin plugin;

    public MaceCombatListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * The Mace is deliberately not Unbreakable (see CustomItems) - so
     * without this, normal combat/use would grind its fake-durability
     * value down and drift it out of the resource pack's texture predicate
     * range. Canceling durability loss entirely keeps it pinned.
     */
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (CustomItems.isMace(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!CustomItems.isMace(weapon)) return;

        LivingEntity target = (LivingEntity) event.getEntity();

        double baseDamage = plugin.getConfig().getDouble("items.mace.base-damage", 7.0);
        double critMultiplier = plugin.getConfig().getDouble("items.mace.smash-crit-multiplier", 1.5);
        float fall = attacker.getFallDistance();
        boolean smash = fall > 1.5f;

        double total = baseDamage;

        if (smash) {
            double bonus = smashTierBonus(fall);

            int densityLevel = EnchantUtil.getLevel(weapon, MaceEnchant.DENSITY);
            if (densityLevel > 0) {
                double perLevel = plugin.getConfig().getDouble("enchantments.density.damage-per-level", 0.5);
                bonus += densityLevel * perLevel * fall;
            }

            total = (baseDamage + bonus) * critMultiplier;

            // A landed smash attack "spends" the fall instead of hurting the player for it.
            attacker.setFallDistance(0f);
        }

        event.setDamage(EntityDamageEvent.DamageModifier.BASE, total);

        int breachLevel = EnchantUtil.getLevel(weapon, MaceEnchant.BREACH);
        if (breachLevel > 0 && event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            double armorReduction = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR); // <= 0
            double ignorePerLevel = plugin.getConfig().getDouble("enchantments.breach.armor-ignore-per-level", 0.15);
            double keepFraction = Math.max(0.0, 1.0 - (breachLevel * ignorePerLevel));
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, armorReduction * keepFraction);
        }

        if (smash) {
            knockback(attacker, target);

            int windBurstLevel = EnchantUtil.getLevel(weapon, MaceEnchant.WIND_BURST);
            if (windBurstLevel > 0) {
                launchAttacker(attacker, windBurstLevel);
                Blast.push(attacker.getLocation(), radius(), pushStrength());
            }
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
        double strength = plugin.getConfig().getDouble("items.mace.knockback-strength", 1.2);
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

    private void launchAttacker(Player attacker, int windBurstLevel) {
        double perLevel = plugin.getConfig().getDouble("enchantments.wind-burst.launch-per-level", 0.9);
        Vector v = attacker.getVelocity();
        v.setY(Math.max(v.getY(), 0) + perLevel * windBurstLevel);
        attacker.setVelocity(v);
        attacker.setFallDistance(0f);
    }

    private double radius() {
        return plugin.getConfig().getDouble("items.wind-charge.radius", 4.0);
    }

    private double pushStrength() {
        return plugin.getConfig().getDouble("items.wind-charge.knockback-strength", 1.6);
    }
}
