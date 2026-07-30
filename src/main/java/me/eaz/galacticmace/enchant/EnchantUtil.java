package me.eaz.galacticmace.enchant;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ground truth for a custom enchant's presence/level is the item's Lore -
 * there is no hidden NBT store. This is a deliberate 1.12.2 design choice:
 *
 * Minecraft 1.12.2 predates CustomModelData (1.14) and has no supported way
 * to register a brand-new enchantment into the CLIENT's translation table,
 * so a "real" custom enchantment NBT entry would render as an unknown/blank
 * tooltip line even if we registered one server-side. Instead:
 *
 *   1. The enchant's name + level is written as a plain, vanilla-styled
 *      Lore line ("Density III") - this is what the player actually reads,
 *      on both maces AND enchanted books, and works on 100% of clients
 *      with zero resource-pack dependency.
 *   2. A single harmless real enchantment (LUCK) is silently added at
 *      level 1 and hidden via ItemFlag.HIDE_ENCHANTS purely so the item
 *      gets the vanilla gold enchantment glint/shimmer. It has no other
 *      effect and is never shown to the player.
 *
 * Because Density + Wind Burst are allowed to coexist, an item can carry
 * more than one of these Lore lines at once - everything here works on
 * the full Map<MaceEnchant,Integer> rather than assuming a single slot.
 */
public final class EnchantUtil {

    private static final String[] ROMAN_VALUES = {"X", "IX", "V", "IV", "I"};
    private static final int[] ROMAN_NUMS = {10, 9, 5, 4, 1};

    private static final Pattern LORE_PATTERN;
    static {
        StringBuilder alt = new StringBuilder();
        for (MaceEnchant e : MaceEnchant.values()) {
            if (alt.length() > 0) alt.append("|");
            alt.append(Pattern.quote(e.getDisplayName()));
        }
        LORE_PATTERN = Pattern.compile("^(" + alt + ") ([IVX]+)$");
    }

    private EnchantUtil() {
    }

    public static String toRoman(int number) {
        StringBuilder sb = new StringBuilder();
        int n = number;
        for (int i = 0; i < ROMAN_NUMS.length; i++) {
            while (n >= ROMAN_NUMS[i]) {
                sb.append(ROMAN_VALUES[i]);
                n -= ROMAN_NUMS[i];
            }
        }
        return sb.length() == 0 ? String.valueOf(number) : sb.toString();
    }

    public static int fromRoman(String roman) {
        int result = 0;
        int i = 0;
        String r = roman.toUpperCase();
        for (int v = 0; v < ROMAN_NUMS.length; v++) {
            while (r.startsWith(ROMAN_VALUES[v], i)) {
                result += ROMAN_NUMS[v];
                i += ROMAN_VALUES[v].length();
            }
        }
        return result == 0 ? -1 : result;
    }

    /** Reads every custom mace-enchant currently present on the item. */
    public static Map<MaceEnchant, Integer> getEnchants(ItemStack item) {
        Map<MaceEnchant, Integer> found = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return found;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return found;

        for (String rawLine : meta.getLore()) {
            String line = ChatColor.stripColor(rawLine).trim();
            Matcher m = LORE_PATTERN.matcher(line);
            if (m.matches()) {
                for (MaceEnchant e : MaceEnchant.values()) {
                    if (e.getDisplayName().equals(m.group(1))) {
                        int lvl = fromRoman(m.group(2));
                        if (lvl > 0) found.put(e, lvl);
                        break;
                    }
                }
            }
        }
        return found;
    }

    public static boolean hasEnchant(ItemStack item, MaceEnchant enchant) {
        return getEnchants(item).containsKey(enchant);
    }

    public static int getLevel(ItemStack item, MaceEnchant enchant) {
        Integer lvl = getEnchants(item).get(enchant);
        return lvl == null ? 0 : lvl;
    }

    /**
     * Adds/upgrades a single enchant on the item using vanilla's combine
     * rule (same level -> +1 capped at max, different levels -> the
     * higher one). Caller is responsible for having already confirmed
     * this won't create an illegal combination (see MaceEnchant#conflictsWith)
     * - this method does not itself reject conflicts, it only writes.
     */
    public static void addOrUpgrade(ItemStack item, MaceEnchant type, int level) {
        Map<MaceEnchant, Integer> current = getEnchants(item);
        Integer cur = current.get(type);
        int newLevel;
        if (cur == null) {
            newLevel = level;
        } else if (cur.intValue() == level) {
            newLevel = cur + 1;
        } else {
            newLevel = Math.max(cur, level);
        }
        newLevel = Math.min(newLevel, type.getMaxLevel());
        current.put(type, newLevel);
        setAllEnchants(item, current);
    }

    /** Fully replaces the set of custom enchants shown on the item. */
    public static void setAllEnchants(ItemStack item, Map<MaceEnchant, Integer> all) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> LORE_PATTERN.matcher(ChatColor.stripColor(line).trim()).matches());
        for (MaceEnchant e : MaceEnchant.values()) {
            Integer lvl = all.get(e);
            if (lvl != null && lvl > 0) {
                lore.add(ChatColor.GRAY + e.getDisplayName() + " " + toRoman(lvl));
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);

        if (all.isEmpty()) {
            removeGlint(item);
        } else {
            applyGlint(item);
        }
    }

    public static void removeAllCustomEnchants(ItemStack item) {
        setAllEnchants(item, new LinkedHashMap<>());
    }

    /**
     * Attempts to merge {@code addition} onto {@code base}, following the
     * same rule Density/Breach/Wind Burst use in real vanilla anvils.
     * Returns {@code null} if the merge would create an illegal
     * combination (caller should then empty the anvil result slot).
     */
    public static Map<MaceEnchant, Integer> mergeAll(Map<MaceEnchant, Integer> base, Map<MaceEnchant, Integer> addition) {
        Map<MaceEnchant, Integer> result = new LinkedHashMap<>(base);
        for (Map.Entry<MaceEnchant, Integer> add : addition.entrySet()) {
            MaceEnchant type = add.getKey();
            int addLevel = add.getValue();

            for (MaceEnchant existing : result.keySet()) {
                if (existing != type && existing.conflictsWith(type)) {
                    return null;
                }
            }

            Integer curLevel = result.get(type);
            int newLevel;
            if (curLevel == null) {
                newLevel = addLevel;
            } else if (curLevel.intValue() == addLevel) {
                newLevel = curLevel + 1;
            } else {
                newLevel = Math.max(curLevel, addLevel);
            }
            result.put(type, Math.min(newLevel, type.getMaxLevel()));
        }
        return result;
    }

    public static void applyGlint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasEnchant(Enchantment.LUCK)) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    public static void removeGlint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta.hasEnchant(Enchantment.LUCK)) {
            meta.removeEnchant(Enchantment.LUCK);
        }
        item.setItemMeta(meta);
    }
}
