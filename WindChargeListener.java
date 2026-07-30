package me.eaz.galacticmace;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wind Charges are a re-skinned Snowball (see CustomItems) - vanilla
 * throwing already works for free, but the actual "push" effect needs to
 * be added by hand.
 *
 * 1.12.2's ProjectileLaunchEvent has no way to ask "which ItemStack was
 * this thrown from" (that came in much later versions), so this
 * correlates the two the standard way: remember for a moment that THIS
 * player just right-clicked our Wind Charge item, then claim the very
 * next Snowball they launch as "theirs" and tag it with metadata.
 */
public class WindChargeListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> pendingThrow = new HashSet<>();

    private static final String META_KEY = "galacticmace_windcharge";

    public WindChargeListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (CustomItems.isWindCharge(item)) {
            pendingThrow.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;

        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Player)) return;

        UUID uuid = ((Player) shooter).getUniqueId();
        if (pendingThrow.remove(uuid)) {
            projectile.setMetadata(META_KEY, new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        Snowball snowball = (Snowball) event.getEntity();
        if (!snowball.hasMetadata(META_KEY)) return;

        Location impact = snowball.getLocation();
        double radius = plugin.getConfig().getDouble("items.wind-charge.radius", 4.0);
        double strength = plugin.getConfig().getDouble("items.wind-charge.knockback-strength", 1.6);

        Blast.push(impact, radius, strength);
    }
}
