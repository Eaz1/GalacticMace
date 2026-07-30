package me.eaz.galacticmace;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Pushes nearby living entities away from (and slightly up from) a point. */
final class Blast {

    private Blast() {
    }

    static void push(Location center, double radius, double strength) {
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
            double power = strength * falloff;

            Vector push = direction.multiply(power);
            push.setY(Math.max(push.getY(), power * 0.5));

            nearby.setVelocity(nearby.getVelocity().add(push));
        }
    }
}
