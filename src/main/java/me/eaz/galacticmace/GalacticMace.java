package me.eaz.galacticmace;

import me.eaz.galacticmace.enchant.LootInjector;
import me.eaz.galacticmace.enchant.MaceEnchant;
import me.eaz.galacticmace.enchant.MaceEnchantListener;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class GalacticMace extends JavaPlugin implements CommandExecutor {

    private static GalacticMace instance;

    private FallImmunity fallImmunity;
    private NetheriteItems netheriteItems;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        MaceEnchant.loadConfig(getConfig());

        fallImmunity = new FallImmunity();
        netheriteItems = new NetheriteItems(this);

        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new MaceEnchantListener(this), this);
        getServer().getPluginManager().registerEvents(new LootInjector(this), this);
        getServer().getPluginManager().registerEvents(new MaceCombatListener(this, fallImmunity), this);
        getServer().getPluginManager().registerEvents(new WindChargeListener(this), this);
        getServer().getPluginManager().registerEvents(fallImmunity, this);

        RecipeLoader recipeLoader = new RecipeLoader(this, netheriteItems);
        recipeLoader.loadAll();
        getServer().getPluginManager().registerEvents(recipeLoader, this);

        if (getCommand("mace") != null) {
            getCommand("mace").setExecutor(this);
        }
        if (getCommand("windcharge") != null) {
            getCommand("windcharge").setExecutor(this);
        }
        if (getCommand("galacticmace") != null) {
            NetheriteCommand netheriteCommand = new NetheriteCommand(this, netheriteItems);
            getCommand("galacticmace").setExecutor(netheriteCommand);
            getCommand("galacticmace").setTabCompleter(netheriteCommand);
        }
        if (getCommand("grindstone") != null) {
            getCommand("grindstone").setExecutor(new GrindstoneCommand());
        }

        // Wind Charges need to stack to 64 - Snowball (their base material) is
        // capped at 16 in vanilla with no per-item override available in
        // 1.12.2, so this is a one-time global bump via NMSUtil. Real plain
        // snowballs stack to 64 too as an accepted side effect - see README.
        NMSUtil.setMaxStackSize(this, Material.SNOW_BALL, 64);

        getLogger().info("=================================");
        getLogger().info("GalacticMace has been enabled!");
        getLogger().info("Mace base item: " + CustomItems.createMace().getType()
                + " (must match the resource pack's diamond_axe.json override)");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("GalacticMace has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("mace")) {
            giveItem(player, CustomItems.createMace());
            player.sendMessage(ChatColor.GRAY + "You received a Mace.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("windcharge")) {
            giveItem(player, CustomItems.createWindCharge());
            player.sendMessage(ChatColor.GREEN + "You received a Wind Charge.");
            return true;
        }

        return false;
    }

    /** Adds the item, dropping it at the player's feet instead if their inventory is full. */
    private void giveItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(overflow -> player.getWorld().dropItem(player.getLocation(), overflow));
    }

    public FallImmunity getFallImmunity() {
        return fallImmunity;
    }

    public NetheriteItems getNetheriteItems() {
        return netheriteItems;
    }

    public static GalacticMace getInstance() {
        return instance;
    }
}
