package com.example.maceplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class MaceListener implements Listener {

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Player player = (Player) event.getDamager();
        LivingEntity victim = (LivingEntity) event.getEntity();

        ItemStack item = player.getInventory().getItemInMainHand();

        if (!CustomItems.isMace(item)) {
            return;
        }

        double damage = event.getDamage();

        /* -------------------------
           Density Enchantment
        -------------------------- */

        int density = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);

        if (player.getFallDistance() > 1.5F) {

            damage += player.getFallDistance() * (1.5 + density);

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ANVIL_LAND,
                    1F,
                    0.7F
            );

            player.getWorld().spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    victim.getLocation(),
                    1
            );
        }

        /* -------------------------
           Breach Enchantment
        -------------------------- */

        int breach = item.getEnchantmentLevel(Enchantment.DAMAGE_ARTHROPODS);

        if (breach > 0) {
            damage += breach * 2.0;
        }

        /* -------------------------
           Wind Burst Enchantment
        -------------------------- */

        int burst = item.getEnchantmentLevel(Enchantment.KNOCKBACK);

        if (burst > 0 && player.getFallDistance() > 1.5F) {

            Location loc = victim.getLocation();

            for (Entity entity : victim.getNearbyEntities(4 + burst, 4, 4 + burst)) {

                if (!(entity instanceof LivingEntity))
                    continue;

                Vector velocity = entity.getLocation()
                        .toVector()
                        .subtract(loc.toVector())
                        .normalize()
                        .multiply(1.2 + (burst * 0.4))
                        .setY(0.7 + (burst * 0.15));

                entity.setVelocity(velocity);
            }

            player.getWorld().playSound(
                    loc,
                    Sound.ENDERDRAGON_WINGS,
                    1F,
                    1.3F
            );
        }

        event.setDamage(damage);
    }
}
