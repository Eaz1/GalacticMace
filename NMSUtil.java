package me.eaz.galacticmace;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 1.12.2's public Bukkit API (ItemMeta) has no way to give a specific item
 * a different attack-damage/armor/toughness/knockback-resistance value
 * than its material's built-in default (ItemMeta#addAttributeModifier
 * wasn't added until a later version - confirmed absent from the actual
 * 1.12.2 javadoc while building this). That's a real functional need for
 * Netherite armor/tools to work against EVERY damage source, not just
 * the Mace, so this is the one place in the plugin that reaches past the
 * public API - via reflection only, so it still compiles against plain
 * spigot-api with zero NMS classes on the classpath.
 *
 * Every method here is defensive: if anything about the server's internal
 * class/field layout doesn't match what's expected, it logs a warning
 * once and returns the item UNCHANGED rather than throwing. Worst case,
 * a Netherite item quietly behaves like its Diamond/Chainmail base
 * instead of crashing the plugin.
 */
final class NMSUtil {

    private static boolean warned = false;
    private static final String NMS_VERSION = detectVersion();

    private NMSUtil() {
    }

    private static String detectVersion() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            return pkg.substring(pkg.lastIndexOf('.') + 1); // e.g. "v1_12_R1"
        } catch (Exception ex) {
            return null;
        }
    }

    private static void warnOnce(JavaPlugin plugin, String context, Throwable t) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("[NMSUtil] Could not apply a custom attribute (" + context
                + ") - affected items will fall back to their base material's stats. Cause: " + t);
    }

    /**
     * Adds a raw attribute modifier (operation 0 = add flat amount) to an
     * ItemStack's AttributeModifiers NBT list, restricted to one equipment
     * slot, and returns the modified item. On any failure, returns the
     * original item unchanged.
     *
     * @param attributeName vanilla attribute key, e.g. "generic.armor",
     *                       "generic.armorToughness", "generic.knockbackResistance",
     *                       "generic.attackDamage"
     * @param slot           "head", "chest", "legs", "feet", or "mainhand"
     */
    static ItemStack addAttribute(JavaPlugin plugin, ItemStack item, String attributeName, double amount, String slot) {
        if (NMS_VERSION == null) return item;
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + NMS_VERSION + ".inventory.CraftItemStack");
            Class<?> nmsItemStackClass = Class.forName("net.minecraft.server." + NMS_VERSION + ".ItemStack");
            Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server." + NMS_VERSION + ".NBTTagCompound");
            Class<?> nbtTagListClass = Class.forName("net.minecraft.server." + NMS_VERSION + ".NBTTagList");
            Class<?> nbtBaseClass = Class.forName("net.minecraft.server." + NMS_VERSION + ".NBTBase");

            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopy.invoke(null, item);

            Method hasTag = nmsItemStackClass.getMethod("hasTag");
            Method getTag = nmsItemStackClass.getMethod("getTag");
            Method setTag = nmsItemStackClass.getMethod("setTag", nbtTagCompoundClass);

            Object tag = (Boolean) hasTag.invoke(nmsItem) ? getTag.invoke(nmsItem) : nbtTagCompoundClass.newInstance();

            Method getList = nbtTagCompoundClass.getMethod("getList", String.class, int.class);
            Method hasKey = nbtTagCompoundClass.getMethod("hasKey", String.class);
            Method setTagMethod = nbtTagCompoundClass.getMethod("set", String.class, nbtBaseClass);

            Object modifiers = (Boolean) hasKey.invoke(tag, "AttributeModifiers")
                    ? getList.invoke(tag, "AttributeModifiers", 10) // 10 = NBTTagCompound id
                    : nbtTagListClass.newInstance();

            Object modifier = nbtTagCompoundClass.newInstance();
            Method setString = nbtTagCompoundClass.getMethod("setString", String.class, String.class);
            Method setDouble = nbtTagCompoundClass.getMethod("setDouble", String.class, double.class);
            Method setIntM = nbtTagCompoundClass.getMethod("setInt", String.class, int.class);
            Method setLong = nbtTagCompoundClass.getMethod("setLong", String.class, long.class);

            UUID uuid = UUID.randomUUID();
            setString.invoke(modifier, "AttributeName", attributeName);
            setString.invoke(modifier, "Name", "galacticmace." + attributeName);
            setDouble.invoke(modifier, "Amount", amount);
            setIntM.invoke(modifier, "Operation", 0);
            setLong.invoke(modifier, "UUIDMost", uuid.getMostSignificantBits());
            setLong.invoke(modifier, "UUIDLeast", uuid.getLeastSignificantBits());
            setString.invoke(modifier, "Slot", slot);

            Method add = nbtTagListClass.getMethod("add", nbtBaseClass);
            add.invoke(modifiers, modifier);
            setTagMethod.invoke(tag, "AttributeModifiers", modifiers);
            setTag.invoke(nmsItem, tag);

            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass);
            return (ItemStack) asBukkitCopy.invoke(null, nmsItem);
        } catch (Throwable t) {
            warnOnce(plugin, attributeName, t);
            return item;
        }
    }

    /**
     * Globally raises a vanilla Item's max stack size via reflection (used
     * to make Snowball - the Wind Charge's base material - stack to 64).
     * This affects EVERY snowball on the server, including plain ones -
     * see WindChargeRecipe/README for why that trade-off was accepted
     * instead of switching base materials.
     */
    static void setMaxStackSize(JavaPlugin plugin, org.bukkit.Material material, int newMax) {
        if (NMS_VERSION == null) return;
        try {
            Class<?> craftMagicNumbersClass = Class.forName("org.bukkit.craftbukkit." + NMS_VERSION + ".util.CraftMagicNumbers");
            Class<?> nmsItemClass = Class.forName("net.minecraft.server." + NMS_VERSION + ".Item");

            Method getItem = craftMagicNumbersClass.getMethod("getItem", org.bukkit.Material.class);
            Object nmsItem = getItem.invoke(null, material);

            Field maxStackSizeField = findField(nmsItemClass, "maxStackSize", "d", "c");
            if (maxStackSizeField == null) {
                throw new NoSuchFieldException("no maxStackSize-like field found on " + nmsItemClass);
            }
            maxStackSizeField.setAccessible(true);
            maxStackSizeField.set(nmsItem, newMax);
        } catch (Throwable t) {
            warnOnce(plugin, "max-stack-size:" + material, t);
        }
    }

    /**
     * Globally raises a vanilla Item's max durability via reflection (used
     * to give Netherite armor/tools their real 1.21 durability numbers,
     * since 1.12.2 has no per-item override - only a material-wide one).
     * This affects EVERY item of that material, including plain ones -
     * see README for why Chainmail/Diamond were chosen specifically to
     * make that trade-off low-impact.
     */
    static void setMaxDurability(JavaPlugin plugin, org.bukkit.Material material, int newMax) {
        if (NMS_VERSION == null) return;
        try {
            Class<?> craftMagicNumbersClass = Class.forName("org.bukkit.craftbukkit." + NMS_VERSION + ".util.CraftMagicNumbers");
            Class<?> nmsItemClass = Class.forName("net.minecraft.server." + NMS_VERSION + ".Item");

            Method getItem = craftMagicNumbersClass.getMethod("getItem", org.bukkit.Material.class);
            Object nmsItem = getItem.invoke(null, material);

            Field durabilityField = findField(nmsItemClass, "maxDurability", "durability", "b", "bb");
            if (durabilityField == null) {
                throw new NoSuchFieldException("no maxDurability-like field found on " + nmsItemClass);
            }
            durabilityField.setAccessible(true);
            durabilityField.set(nmsItem, newMax);
        } catch (Throwable t) {
            warnOnce(plugin, "max-durability:" + material, t);
        }
    }

    /** Tries several possible (obfuscated or mapped) field names in order, returns the first that exists. */
    private static Field findField(Class<?> clazz, String... candidates) {
        for (String name : candidates) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // try the next candidate
            }
        }
        return null;
    }
}
