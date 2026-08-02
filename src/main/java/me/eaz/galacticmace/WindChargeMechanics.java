package me.eaz.galacticmace;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Everything about "what a burst of wind does to nearby entities," shared
 * by a thrown Wind Charge landing and a Wind Burst-enchanted smash attack,
 * so the two features can't drift apart or double-implement the same math.
 */
final class WindChargeMechanics {

    private WindChargeMechanics() {
    }

    /** The area push: knocks back every living entity within radius, including the source itself if it's in range. */
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
            if (!(nearby instanceof LivingEntity)) continue;

            Vector direction = nearby.getLocation().toVector().subtract(center.toVector());
            double distance = direction.length();

            if (distance < 0.001) {
                direction = new Vector(0, 1, 0);
                distance = 0.001;
            } else {
                direction.normalize();
            }

            double falloff = Math.max(0.15, 1.0 - (distance / radius));
            double blastReduction = 1.0 - Math.min(1.0, CombatMath.totalBlastProtection((LivingEntity) nearby) * 0.15);
            double power = strength * falloff * blastReduction;

            Vector push = direction.multiply(power);
            push.setY(Math.max(push.getY(), power * 0.5));

            nearby.setVelocity(nearby.getVelocity().add(push));

            if (nearby instanceof Player) {
                GalacticMace.getInstance().getFallImmunity().grant(nearby);
            }
        }
    }

    /**
     * The Wind Burst enchant's own launch component - an extra vertical
     * boost applied directly to the attacker on top of the area push
     * above, scaled by enchant level and reduced by the attacker's own
     * Blast Protection (matching how vanilla lets armor soften it).
     */
    static void launchUpward(Player attacker, int windBurstLevel, double blastReduction) {
        double perLevel = GalacticMace.getInstance().getConfig().getDouble("enchantments.wind-burst.launch-per-level", 0.55);

        Vector v = attacker.getVelocity();
        double boost = perLevel * windBurstLevel * blastReduction;
        v.setY(Math.max(v.getY(), 0) + boost);
        attacker.setVelocity(v);
    }
}
