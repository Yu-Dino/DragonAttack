package net.yuflow.dragonattack;

import org.bukkit.plugin.java.JavaPlugin;

public final class Dragonattack extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("DragonAttack wurde für Folia aktiviert!");
        getServer().getPluginManager().registerEvents(new DragonListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("DragonAttack wurde deaktiviert!");
    }
}