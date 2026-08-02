package me.eaz.galacticmace;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared by MaceCombatListener (a landed smash attack negates that fall)
 * and WindChargeListener (being pushed by a Wind Charge/Wind Burst
 * negates the resulting fall) - both just call grant(entity) and this
 * handles the actual EntityDamageEvent cancellation, one use each.
 */
public class FallImmunity implements Listener {

    private final Set<UUID> immune = new HashSet<>();

    public void grant(Entity entity) {
        immune.add(entity.getUniqueId());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (immune.remove(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
