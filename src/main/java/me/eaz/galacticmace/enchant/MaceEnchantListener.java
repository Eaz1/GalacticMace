package me.eaz.galacticmace.enchant;

import me.eaz.galacticmace.CustomItems;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles every path the requirements call for:
 *  - PrepareItemEnchantEvent / EnchantItemEvent -> enchanting table (mace + plain books)
 *  - PrepareAnvilEvent                          -> book+book, mace+book, mace+mace merging,
 *                                                   with illegal combinations blocked
 *
 * NOT handled: grindstones. Grindstones do not exist in Minecraft 1.12.2
 * (added in 1.14) - there is no vanilla feature here to hook into. If this
 * plugin is ever ported to 1.14+, PrepareGrindstoneEvent could strip these
 * same Lore-based enchants using EnchantUtil.removeAllCustomEnchants().
 */
public class MaceEnchantListener implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    /** enchanter UUID -> the custom enchant/level rolled for each of the 3 table slots this GUI session. */
    private final Map<UUID, RolledOffer[]> pendingOffers = new HashMap<>();

    public MaceEnchantListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean eligibleForTable(ItemStack item) {
        if (item == null) return false;
        if (CustomItems.isMace(item)) return true;
        return item.getType() == Material.BOOK;
    }

    // =========================================================
    //  ENCHANTING TABLE
    // =========================================================

    @EventHandler
    public void onPrepare(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (!eligibleForTable(item)) return;

        Map<MaceEnchant, Integer> already = EnchantUtil.getEnchants(item);
        List<MaceEnchant> pool = new ArrayList<>();
        for (MaceEnchant candidate : MaceEnchant.values()) {
            boolean blocked = false;
            for (MaceEnchant have : already.keySet()) {
                if (have != candidate && have.conflictsWith(candidate)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) pool.add(candidate);
        }
        if (pool.isEmpty()) return; // item already carries every legal enchant it can hold

        int bonus = event.getEnchantmentBonus();
        EnchantmentOffer[] offers = event.getOffers();
        RolledOffer[] rolled = new RolledOffer[3];

        for (int i = 0; i < 3 && i < offers.length; i++) {
            int cost = costForSlot(i, bonus);
            MaceEnchant chosen = weightedPick(pool);
            int level = levelForCost(chosen, cost);
            offers[i] = new EnchantmentOffer(Enchantment.LUCK, level, cost);
            rolled[i] = new RolledOffer(chosen, level);
        }

        pendingOffers.put(event.getEnchanter().getUniqueId(), rolled);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (!eligibleForTable(item)) return;

        UUID uuid = event.getEnchanter().getUniqueId();
        RolledOffer[] rolled = pendingOffers.remove(uuid);
        int slot = event.whichButton();
        if (rolled == null || slot < 0 || slot >= rolled.length || rolled[slot] == null) {
            return;
        }

        if (item.getType() == Material.BOOK) {
            item.setType(Material.ENCHANTED_BOOK);
        }

        EnchantUtil.addOrUpgrade(item, rolled[slot].enchant, rolled[slot].level);
    }

    private int costForSlot(int slot, int bonus) {
        int base;
        switch (slot) {
            case 0:
                base = Math.max(1, bonus / 3);
                break;
            case 1:
                base = Math.max(2, (bonus * 2) / 3 + 1);
                break;
            default:
                base = Math.max(3, bonus);
                break;
        }
        return Math.min(30, base + random.nextInt(3) + 1);
    }

    private int levelForCost(MaceEnchant enchant, int cost) {
        int max = enchant.getMaxLevel();
        int level = 1 + Math.round((cost / 30f) * (max - 1));
        return Math.max(1, Math.min(max, level));
    }

    private MaceEnchant weightedPick(List<MaceEnchant> pool) {
        int total = 0;
        for (MaceEnchant e : pool) total += e.getWeight();
        int roll = random.nextInt(total);
        int cursor = 0;
        for (MaceEnchant e : pool) {
            cursor += e.getWeight();
            if (roll < cursor) return e;
        }
        return pool.get(pool.size() - 1);
    }

    private static final class RolledOffer {
        final MaceEnchant enchant;
        final int level;

        RolledOffer(MaceEnchant enchant, int level) {
            this.enchant = enchant;
            this.level = level;
        }
    }

    // =========================================================
    //  ANVIL
    // =========================================================

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || right == null) return;

        Map<MaceEnchant, Integer> leftEnch = EnchantUtil.getEnchants(left);
        Map<MaceEnchant, Integer> rightEnch = EnchantUtil.getEnchants(right);
        if (leftEnch.isEmpty() && rightEnch.isEmpty()) return; // not our business, let vanilla/default plugin logic run

        boolean leftIsMace = CustomItems.isMace(left);
        boolean rightIsMace = CustomItems.isMace(right);
        boolean leftIsBook = left.getType() == Material.ENCHANTED_BOOK;
        boolean rightIsBook = right.getType() == Material.ENCHANTED_BOOK;

        boolean supported = (leftIsMace && rightIsBook)
                || (leftIsBook && rightIsBook)
                || (leftIsMace && rightIsMace);
        if (!supported) return;

        // Preserve any rename the player typed - vanilla already computed a
        // default result (possibly renamed) before this event fires.
        String renamedTo = null;
        ItemStack vanillaDefault = event.getResult();
        if (vanillaDefault != null && vanillaDefault.hasItemMeta()) {
            ItemMeta vm = vanillaDefault.getItemMeta();
            if (vm.hasDisplayName()) renamedTo = vm.getDisplayName();
        }

        Map<MaceEnchant, Integer> merged = EnchantUtil.mergeAll(leftEnch, rightEnch);
        if (merged == null) {
            // Illegal combination (e.g. Breach + Density/Wind Burst) - block it,
            // matching vanilla's "no valid result" behavior.
            event.setResult(null);
            return;
        }

        ItemStack result = left.clone();
        result.setAmount(1);

        if (leftIsMace) {
            // Never let the merge disturb the fixed fake-durability value the
            // resource pack's diamond_hoe.json predicate depends on.
            short max = result.getType().getMaxDurability();
            result.setDurability((short) Math.floor(max * 0.995));
        }

        EnchantUtil.setAllEnchants(result, merged);

        if (renamedTo != null) {
            ItemMeta rm = result.getItemMeta();
            rm.setDisplayName(renamedTo);
            result.setItemMeta(rm);
        }

        inv.setRepairCost(anvilCost(rightEnch));
        event.setResult(result);
    }

    private int anvilCost(Map<MaceEnchant, Integer> addedSide) {
        int perLevel = plugin.getConfig().getInt("enchantments.anvil.cost-per-level", 2);
        int cost = 0;
        for (int lvl : addedSide.values()) {
            cost += lvl * perLevel;
        }
        return Math.max(1, cost);
    }
}
