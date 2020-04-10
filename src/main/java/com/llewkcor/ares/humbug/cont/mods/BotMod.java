package com.llewkcor.ares.humbug.cont.mods;

import com.google.common.collect.Lists;
import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.commons.util.general.IPS;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.List;

public final class BotMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "Bot Protection";
    @Getter @Setter public boolean enabled;

    @Getter public boolean limitConnections;
    @Getter public int maxConnectionsPerIP;

    public BotMod(Humbug plugin) {
        this.plugin = plugin;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.limitConnections = config.getBoolean("mods.bots.limit_connections");
        this.maxConnectionsPerIP = config.getInt("mods.bots.max_conn_per_ip");
    }

    @Override
    public void unload() {

    }

    @EventHandler
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        final long address = IPS.toLong(event.getAddress().getHostAddress());

        if (isLimitConnections() && getMaxConnectionsPerIP() < getOpenConnections(address)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Too many accounts connected with your IP address");
        }
    }

    /**
     * Returns a count of how many other players are connected with the same IP Address
     * @param address Address
     * @return Amount of connected IPS that match the provided IP
     */
    private int getOpenConnections(long address) {
        final List<Long> addresses = Lists.newArrayList();
        Bukkit.getOnlinePlayers().forEach(player -> addresses.add(IPS.toLong(player.getAddress().getAddress().getHostAddress())));
        return (int) addresses.stream().filter(addr -> addr == address).count();
    }
}
