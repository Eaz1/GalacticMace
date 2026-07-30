package me.eaz.galacticmace;

import me.eaz.galacticmace.enchant.LootInjector;
import me.eaz.galacticmace.enchant.MaceEnchant;
import me.eaz.galacticmace.enchant.MaceEnchantListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class GalacticMace extends JavaPlugin implements CommandExecutor {

    private static GalacticMace instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        MaceEnchant.loadConfig(getConfig());

        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new MaceEnchantListener(this), this);
        getServer().getPluginManager().registerEvents(new LootInjector(this), this);
        getServer().getPluginManager().registerEvents(new MaceCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new WindChargeListener(this), this);

        MaceRecipe maceRecipe = new MaceRecipe(this);
        maceRecipe.register();
        getServer().getPluginManager().registerEvents(maceRecipe, this);

        new WindChargeRecipe(this).register();

        if (getCommand("mace") != null) {
            getCommand("mace").setExecutor(this);
        }
        if (getCommand("windcharge") != null) {
            getCommand("windcharge").setExecutor(this);
        }

        getLogger().info("=================================");
        getLogger().info("GalacticMace has been enabled!");
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
            ItemStack mace = CustomItems.createMace();
            leftover(player, mace);
            player.sendMessage(ChatColor.GRAY + "You received a Mace.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("windcharge")) {
            ItemStack windCharge = CustomItems.createWindCharge();
            leftover(player, windCharge);
            player.sendMessage(ChatColor.GREEN + "You received a Wind Charge.");
            return true;
        }

        return false;
    }

    /** Adds the item, dropping it at the player's feet instead if their inventory is full. */
    private void leftover(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(overflow -> player.getWorld().dropItem(player.getLocation(), overflow));
    }

    public static GalacticMace getInstance() {
        return instance;
    }
}
