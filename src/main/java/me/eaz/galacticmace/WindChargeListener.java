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
        projectile.setGravity(plugin.getConfig().getBoolean("wind-charge.gravity", false));
        double speed = plugin.getConfig().getDouble("wind-charge.speed", 2.2);
        int cooldownTicks = plugin.getConfig().getInt("wind-charge.cooldown-ticks", 8);
        int lifetimeTicks = plugin.getConfig().getInt("wind-charge.lifetime-ticks", 100);
        projectile.setVelocity(player.getLocation().getDirection().normalize().multiply(speed));
        player.setCooldown(Material.SNOW_BALL, cooldownTicks);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (projectile.isValid()) projectile.remove();
        }, lifetimeTicks);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        Snowball snowball = (Snowball) event.getEntity();
        if (!snowball.hasMetadata(META_KEY)) return;

        // pushNearby now explicitly includes LivingEntity, ArmorStand and EnderPearl.
        // ProjectileHitEvent therefore produces one consistent blast for both
        // block and entity impacts without applying the hit twice.
        WindChargeMechanics.pushNearby(plugin, snowball.getLocation());
        snowball.remove();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Snowball && event.getDamager().hasMetadata(META_KEY)) {
            event.setCancelled(true);
        }
    }
}
