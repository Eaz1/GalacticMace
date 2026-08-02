package me.eaz.galacticmace;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 * Wind Charges are a re-skinned Snowball (see CustomItems) - the base
 * material is kept specifically so the existing throw-on-right-click,
 * stacking, and event lifecycle all keep working; what changes here is
 * everything about HOW it flies and what it does on impact.
 *
 * Straight-line flight is achieved by disabling the projectile's gravity
 * and giving it a fixed velocity along the shooter's look direction the
 * instant it launches - both confirmed-available on Entity in 1.12.2
 * (setGravity) rather than switching to a different base entity, which
 * would have reopened the whole "does this new entity have side effects
 * to suppress" question the Mace's hoe-vs-axe switch already ran into.
 *
 * 1.12.2 has no way to ask "which ItemStack was this projectile thrown
 * from," so the same pending-throw correlation trick from before is
 * still how a real Wind Charge throw gets distinguished from a plain
 * vanilla snowball throw.
 */
public class WindChargeListener implements Listener {

    private static final String META_KEY = "galacticmace_windcharge";

    private final JavaPlugin plugin;
    private final Set<UUID> pendingThrow = new HashSet<>();

    public WindChargeListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!CustomItems.isWindCharge(item)) return;

        Player player = event.getPlayer();
        if (player.hasCooldown(Material.SNOW_BALL)) {
            event.setCancelled(true);
            return;
        }

        pendingThrow.add(player.getUniqueId());
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;

        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Player)) return;

        Player player = (Player) shooter;
        if (!pendingThrow.remove(player.getUniqueId())) return;

        projectile.setMetadata(META_KEY, new FixedMetadataValue(plugin, true));

        boolean gravity = plugin.getConfig().getBoolean("wind-charge.gravity", false);
        double speed = plugin.getConfig().getDouble("wind-charge.speed", 2.2);
        int cooldownTicks = plugin.getConfig().getInt("wind-charge.cooldown-ticks", 8);
        int lifetimeTicks = plugin.getConfig().getInt("wind-charge.lifetime-ticks", 100);

        projectile.setGravity(gravity);
        projectile.setVelocity(player.getLocation().getDirection().normalize().multiply(speed));

        player.setCooldown(Material.SNOW_BALL, cooldownTicks);

        // Safety-net despawn in case it never hits anything (e.g. thrown into open sky with gravity off).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (projectile.isValid()) {
                projectile.remove();
            }
        }, lifetimeTicks);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        Snowball snowball = (Snowball) event.getEntity();
        if (!snowball.hasMetadata(META_KEY)) return;

        WindChargeMechanics.pushNearby(plugin, snowball.getLocation());
    }

    /** No explosion damage, ever - even the 3 damage a vanilla snowball deals to Blazes/Endermen is suppressed. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Snowball)) return;
        if (event.getDamager().hasMetadata(META_KEY)) {
            event.setCancelled(true);
        }
    }
}
