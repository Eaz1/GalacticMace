package me.eaz.galacticmace;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends the configured resource pack on join and reacts to whether it
 * loaded. No texture-swapping is needed for "fallback" - the custom
 * items are built on real vanilla item ids (DIAMOND_AXE, SNOW_BALL), so
 * a player without the pack simply sees the normal vanilla axe/snowball
 * model instead of the mace/wind-charge model. The item's NAME, LORE,
 * and BEHAVIOR (damage, knockback, etc.) work identically either way -
 * only the appearance degrades gracefully.
 */
public class ResourcePackListener implements Listener {

    private final JavaPlugin plugin;

    public ResourcePackListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("resource-pack.prompt-on-join", true)) return;

        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isEmpty() || url.contains("example.com")) {
            plugin.getLogger().warning("resource-pack.url is not configured in config.yml - " +
                    "players will only see vanilla textures for custom items.");
            return;
        }

        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        Player player = event.getPlayer();

        // Delay slightly so the pack prompt doesn't race the join screen.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sha1 != null && !sha1.isEmpty()) {
                player.setResourcePack(url, hexToBytes(sha1));
            } else {
                player.setResourcePack(url);
            }
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();

        switch (event.getStatus()) {
            case DECLINED:
            case FAILED_DOWNLOAD:
                String msg = plugin.getConfig().getString(
                        "resource-pack.decline-message",
                        "You declined the resource pack - custom items will use their vanilla look.");
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
                break;
            case SUCCESSFULLY_LOADED:
                // Nothing to do - custom models will now render correctly.
                break;
            case ACCEPTED:
                // Download in progress.
                break;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
