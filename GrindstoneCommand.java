package me.eaz.galacticmace;

import me.eaz.galacticmace.enchant.EnchantUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Set;

/**
 * /grindstone - 1.12.2 has no real Grindstone block (added 1.14), so this
 * is a command-based stand-in that does exactly what one does: strips
 * enchantments from the held item and nothing else.
 *
 * Two enchant systems get cleared here:
 *   - Real vanilla NBT enchantments (Sharpness, Unbreaking, etc, plus the
 *     hidden LUCK marker our own items use purely for the glint effect)
 *   - Our own Lore-based Density/Breach/Wind Burst lines (EnchantUtil
 *     already knows how to strip exactly those lines and nothing else)
 *
 * Curse of Vanishing survives both. Display name, non-enchant Lore,
 * attribute modifiers, durability/damage, and everything else about the
 * item are never touched.
 */
public class GrindstoneCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("galacticmace.grindstone")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must be holding an item.");
            return true;
        }

        // Our Lore-based enchants (Density/Breach/Wind Burst) - also clears
        // the hidden glint marker if nothing is left afterward.
        EnchantUtil.removeAllCustomEnchants(item);

        // Real vanilla enchantments, except Curse of Vanishing.
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasEnchants()) {
                Set<Enchantment> toRemove = new HashSet<>(meta.getEnchants().keySet());
                toRemove.remove(Enchantment.VANISHING_CURSE);
                for (Enchantment e : toRemove) {
                    meta.removeEnchant(e);
                }
                item.setItemMeta(meta);
            }
        }

        player.sendMessage(ChatColor.GRAY + "Enchantments removed.");
        return true;
    }
}
