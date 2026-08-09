package me.eaz.galacticmace;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Shared Wind Charge / Wind Burst knockback mechanics. */
final class WindChargeMechanics {

    private WindChargeMechanics() {
    }

    static void pushNearby(JavaPlugin plugin, Location center) {
        double radius = plugin.getConfig().getDouble("wind-charge.radius", 4.0);
        double strength = plugin.getConfig().getDouble("wind-charge.knockback-strength", 1.6);

        if (plugin.getConfig().getBoolean("wind-charge.particles", true)) {
            center.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center, 1, 0, 0, 0, 0);
        }
        if (plugin.getConfig().getBoolean("wind-charge.sounds", true)) {
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        }

        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            pushEntity(plugin, center, nearby, radius, strength);
        }
    }

    /**
     * Explicitly supports LivingEntity, ArmorStand and EnderPearl. ArmorStand
     * and EnderPearl are not LivingEntity, so they must never be filtered out.
     */
    static void pushEntity(JavaPlugin plugin, Location center, Entity entity, double radius, double strength) {
        if (entity == null || !entity.isValid()) return;
        if (!(entity instanceof LivingEntity) && !(entity instanceof ArmorStand) && !(entity instanceof EnderPearl)) return;

        Vector direction = entity.getLocation().toVector().subtract(center.toVector());
        double distance = direction.length();

        if (distance > radius) return;
        if (distance < 0.001) {
            direction = new Vector(0, 1, 0);
            distance = 0.001;
        } else {
            direction.normalize();
        }

        double falloff = Math.max(0.15, 1.0 - (distance / radius));
        double blastReduction = 1.0;
        if (entity instanceof LivingEntity) {
            blastReduction = 1.0 - Math.min(1.0, CombatMath.totalBlastProtection((LivingEntity) entity) * 0.15);
        }

        double power = strength * falloff * blastReduction;
        Vector push = direction.multiply(power);
        push.setY(Math.max(push.getY(), power * 0.5));
        entity.setVelocity(entity.getVelocity().add(push));

        if (entity instanceof Player) {
            GalacticMace.getInstance().getFallImmunity().grant(entity);
        }
    }

    static void launchUpward(Player attacker, int windBurstLevel, double blastReduction) {
        double perLevel = GalacticMace.getInstance().getConfig().getDouble("enchantments.wind-burst.launch-per-level", 0.55);
        Vector v = attacker.getVelocity();
        double boost = perLevel * windBurstLevel * blastReduction;
        v.setY(Math.max(v.getY(), 0) + boost);
        attacker.setVelocity(v);
    }
}
