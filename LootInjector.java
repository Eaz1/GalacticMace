package me.eaz.galacticmace.enchant;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Best-effort "obtainable from generated loot" support.
 *
 * IMPORTANT LIMITATION: Minecraft 1.12.2's Bukkit API has no
 * LootGenerateEvent (that was added in 1.14) and no public way to ask
 * "was this chest just populated from a vanilla loot table?". The
 * heuristic used here is: the FIRST time any single (non-double) chest
 * is opened on the server, there's a configurable chance to drop one
 * custom-enchanted book into an empty slot.
 *
 * This will also fire on the first-ever open of a chest a player placed
 * and is about to fill themselves - there is no reliable way to
 * distinguish that from a natural dungeon/structure chest without NMS
 * access to the loot-table NBT tag. Keep the default chance low, or
 * leave this feature disabled (default) if that trade-off isn't
 * acceptable for your server.
 */
public class LootInjector implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Set<String> seen = new HashSet<>();
    private final File dataFile;

    public LootInjector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "loot-chests-seen.txt");
        load();
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) seen.add(line.trim());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read loot-chests-seen.txt: " + ex.getMessage());
        }
    }

    private void markSeen(String key) {
        seen.add(key);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            try (FileWriter w = new FileWriter(dataFile, true)) {
                w.write(key);
                w.write("\n");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write loot-chests-seen.txt: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("enchantments.loot.enabled", false)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Chest)) return; // single chests only; double chests skipped for simplicity

        Chest chest = (Chest) holder;
        Location loc = chest.getLocation();
        String key = loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        if (seen.contains(key)) return;
        markSeen(key);

        double chance = plugin.getConfig().getDouble("enchantments.loot.chance", 0.08);
        if (random.nextDouble() > chance) return;

        Inventory inv = event.getInventory();
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType() == Material.AIR) empty.add(i);
        }
        if (empty.isEmpty()) return;

        MaceEnchant type = weightedPick();
        int level = 1 + random.nextInt(Math.max(1, (type.getMaxLevel() + 1) / 2));

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantUtil.addOrUpgrade(book, type, level);

        inv.setItem(empty.get(random.nextInt(empty.size())), book);
    }

    private MaceEnchant weightedPick() {
        int total = 0;
        for (MaceEnchant e : MaceEnchant.values()) total += e.getWeight();
        int roll = random.nextInt(total);
        int cursor = 0;
        for (MaceEnchant e : MaceEnchant.values()) {
            cursor += e.getWeight();
            if (roll < cursor) return e;
        }
        return MaceEnchant.values()[0];
    }
}
