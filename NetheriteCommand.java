package me.eaz.galacticmace;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /galacticmace netherite <helmet|chestplate|leggings|boots|sword|pickaxe|axe|shovel|hoe> [player] */
public class NetheriteCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PIECES = Arrays.asList(
            "helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe");

    private final JavaPlugin plugin;
    private final NetheriteItems items;

    public NetheriteCommand(JavaPlugin plugin, NetheriteItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("netherite")) {
            sender.sendMessage(ChatColor.RED + "Usage: /galacticmace netherite <piece> [player]");
            return true;
        }
        if (!sender.hasPermission("galacticmace.netherite")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /galacticmace netherite <" + String.join("|", PIECES) + "> [player]");
            return true;
        }

        String piece = args[1].toLowerCase();
        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[2] + "' not found or offline.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player.");
            return true;
        }

        ItemStack item = createPiece(piece);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Unknown piece '" + piece + "'. Options: " + String.join(", ", PIECES));
            return true;
        }

        target.getInventory().addItem(item).values()
                .forEach(overflow -> target.getWorld().dropItem(target.getLocation(), overflow));
        sender.sendMessage(ChatColor.DARK_GRAY + "Gave " + target.getName() + " a Netherite " + capitalize(piece) + ".");
        return true;
    }

    private ItemStack createPiece(String piece) {
        switch (piece) {
            case "helmet": return items.createHelmet();
            case "chestplate": return items.createChestplate();
            case "leggings": return items.createLeggings();
            case "boots": return items.createBoots();
            case "sword": return items.createSword();
            case "pickaxe": return items.createPickaxe();
            case "axe": return items.createAxe();
            case "shovel": return items.createShovel();
            case "hoe": return items.createHoe();
            default: return null;
        }
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Collections.singletonList("netherite"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("netherite")) {
            return filterStartsWith(PIECES, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("netherite")) {
            List<String> names = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filterStartsWith(names, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(prefix.toLowerCase())) result.add(o);
        }
        return result;
    }
}
