package com.llewkcor.ares.humbug.cont.mods;

import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class WorldMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "World";
    @Getter @Setter public boolean enabled;

    @Getter public boolean enderchestDisabled;
    @Getter public boolean blockExplosionsDisabled;
    @Getter public boolean entityBlockChangesDisabled;
    @Getter public boolean firespreadDisabled;
    @Getter public boolean cobblestoneGenDisabled;
    @Getter public boolean bedbombDisabled;

    public WorldMod(Humbug plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.enabled = config.getBoolean("mods.world.enabled");
        this.enderchestDisabled = config.getBoolean("mods.world.enderchests");
        this.blockExplosionsDisabled = config.getBoolean("mods.world.block_explosions");
        this.entityBlockChangesDisabled = config.getBoolean("mods.world.entity_block_changes");
        this.firespreadDisabled = config.getBoolean("mods.world.fire_spread");
        this.cobblestoneGenDisabled = config.getBoolean("mods.world.cobblestone_generators");
        this.bedbombDisabled = config.getBoolean("mods.world.bedbombs");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unload() {
        this.enabled = false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock();

        if (block == null || !block.getType().equals(Material.ENDER_CHEST)) {
            return;
        }

        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (!isEnabled() || !isEnderchestDisabled()) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Enderchests have been disabled");
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isEnabled() || !isBlockExplosionsDisabled()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        if (!isEnabled() || !isEntityBlockChangesDisabled()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onFireSpread(BlockSpreadEvent event) {
        if (!isEnabled() || !isFirespreadDisabled()) {
            return;
        }

        if (!event.getSource().getType().equals(Material.FIRE)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        if (!isEnabled() || !isCobblestoneGenDisabled()) {
            return;
        }

        final Block block = event.getNewState().getBlock();

        if (block.getType().equals(Material.COBBLESTONE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnterBed(PlayerInteractEvent event) {
        if (!isEnabled() || !isBedbombDisabled()) {
            return;
        }

        final Block block = event.getClickedBlock();
        final Action action = event.getAction();

        if (!action.equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (block == null || !(block.getType().equals(Material.BED) || block.getType().equals(Material.BED_BLOCK))) {
            return;
        }

        if (!block.getWorld().getEnvironment().equals(World.Environment.NETHER)) {
            return;
        }

        event.setCancelled(true);
    }
}