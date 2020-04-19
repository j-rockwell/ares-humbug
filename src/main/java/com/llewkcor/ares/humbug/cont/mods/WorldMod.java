package com.llewkcor.ares.humbug.cont.mods;

import com.llewkcor.ares.commons.logger.Logger;
import com.llewkcor.ares.commons.util.bukkit.Scheduler;
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
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
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

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.enderchestDisabled = config.getBoolean("mods.world.disable_enderchests");
        this.blockExplosionsDisabled = config.getBoolean("mods.world.disable_block_explosions");
        this.entityBlockChangesDisabled = config.getBoolean("mods.world.disable_entity_block_changes");
        this.firespreadDisabled = config.getBoolean("mods.world.disable_fire_spread");
        this.cobblestoneGenDisabled = config.getBoolean("mods.world.disable_cobblestone_generators");
        this.bedbombDisabled = config.getBoolean("mods.world.disable_bedbombs");

        this.enabled = true;
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

        event.blockList().clear();
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
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!isEnabled() || !isCobblestoneGenDisabled()) {
            return;
        }

        final Block source = event.getBlock();
        final Block to = event.getToBlock();
        final Material mirrorID1 = (source.getType().equals(Material.WATER) || source.getType().equals(Material.STATIONARY_WATER) ? Material.LAVA : Material.WATER);
        final Material mirrorID2 = (source.getType().equals(Material.WATER) || source.getType().equals(Material.STATIONARY_WATER) ? Material.STATIONARY_LAVA : Material.STATIONARY_WATER);
        final BlockFace[] faces = new BlockFace[] {BlockFace.SELF, BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };

        for (BlockFace face : faces){
            final Block relative = to.getRelative(face, 1);

            if (relative.getType().equals(mirrorID1) || relative.getType().equals(mirrorID2)) {
                event.setCancelled(true);
                return;
            }
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