package me.eaz.galacticmace;

import org.bukkit.plugin.java.JavaPlugin;

public class GalacticMace extends JavaPlugin {

    private static GalacticMace instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("=================================");
        getLogger().info("GalacticMace has been enabled!");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("GalacticMace has been disabled!");
    }

    public static GalacticMace getInstance() {
        return instance;
    }
}
