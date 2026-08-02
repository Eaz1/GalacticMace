package me.eaz.galacticmace.enchant;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * The three mace-exclusive enchantments from vanilla 1.21, reimplemented
 * as a self-contained custom enchant system (see EnchantUtil for why -
 * short version: Minecraft 1.12.2 has no CustomModelData/data-driven
 * enchantment registry, so these are NOT real registered
 * org.bukkit.enchantments.Enchantment objects; they're tracked entirely
 * in item Lore and applied/read by this plugin).
 *
 * Compatibility (matches real vanilla behavior):
 *   - Density   <-> Breach     : INCOMPATIBLE
 *   - Breach    <-> Wind Burst : INCOMPATIBLE
 *   - Density   <-> Wind Burst : COMPATIBLE (both may exist together)
 */
public enum MaceEnchant {

    DENSITY("Density", "density", 5, 10),
    BREACH("Breach", "breach", 4, 5),
    WIND_BURST("Wind Burst", "wind-burst", 3, 5);

    private final String displayName;
    private final String key;
    private final int defaultMaxLevel;
    private final int defaultWeight;

    // Mutable, set once from config.yml at startup via loadConfig(). Falls
    // back to the vanilla-accurate defaults above if config.yml omits them.
    private int maxLevel;
    private int weight;

    MaceEnchant(String displayName, String key, int defaultMaxLevel, int defaultWeight) {
        this.displayName = displayName;
        this.key = key;
        this.defaultMaxLevel = defaultMaxLevel;
        this.defaultWeight = defaultWeight;
        this.maxLevel = defaultMaxLevel;
        this.weight = defaultWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKey() {
        return key;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getWeight() {
        return weight;
    }

    /** True if this enchant and {@code other} may never coexist on the same item. */
    public boolean conflictsWith(MaceEnchant other) {
        if (other == null || other == this) return false;
        // Breach conflicts with both Density and Wind Burst.
        // Density and Wind Burst do NOT conflict with each other.
        return this == BREACH || other == BREACH;
    }

    public static MaceEnchant byKey(String key) {
        for (MaceEnchant e : values()) {
            if (e.key.equalsIgnoreCase(key)) return e;
        }
        return null;
    }

    /** Reads enchantments.<key>.max-level / .weight from config.yml, if present. */
    public static void loadConfig(FileConfiguration cfg) {
        for (MaceEnchant e : values()) {
            String base = "enchantments." + e.key;
            e.maxLevel = Math.max(1, cfg.getInt(base + ".max-level", e.defaultMaxLevel));
            e.weight = Math.max(1, cfg.getInt(base + ".weight", e.defaultWeight));
        }
    }
}
