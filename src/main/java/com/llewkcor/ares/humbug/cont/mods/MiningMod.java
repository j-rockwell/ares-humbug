package com.llewkcor.ares.humbug.cont.mods;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.llewkcor.ares.commons.location.BLocatable;
import com.llewkcor.ares.commons.logger.Logger;
import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public final class MiningMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "Mining";
    @Getter @Setter public boolean enabled;

    @Getter public final List<Findable> findables;
    @Getter public final List<BLocatable> placedStone;
    @Getter public final Random random;

    public MiningMod(Humbug plugin) {
        this.plugin = plugin;
        this.findables = Lists.newArrayList();
        this.placedStone = Lists.newArrayList();
        this.random = new Random();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void load() {
        if (!findables.isEmpty()) {
            findables.clear();
        }

        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.enabled = config.getBoolean("mods.mining.enabled");

        for (String rewardMaterialName : config.getConfigurationSection("mods.mining.findables").getKeys(false)) {
            final String insideMaterialName = config.getString("mods.mining.findables." + rewardMaterialName + ".inside");
            final String environmentName = config.getString("mods.mining.findables." + rewardMaterialName + ".environment");
            final int minVeinSize = config.getInt("mods.mining.findables." + rewardMaterialName + ".min_vein");
            final int maxVeinSize = config.getInt("mods.mining.findables." + rewardMaterialName + ".max_vein");
            final int minSpawnHeight = config.getInt("mods.mining.findables." + rewardMaterialName + ".min_spawn_height");
            final int maxSpawnHeight = config.getInt("mods.mining.findables." + rewardMaterialName + ".max_spawn_height");
            final String oddsPreSplit = config.getString("mods.mining.findables." + rewardMaterialName + ".odds");
            final String[] oddsSplit = oddsPreSplit.split(":");
            final int requiredPull = Integer.parseInt(oddsSplit[0]);
            final int totalPull = Integer.parseInt(oddsSplit[1]);

            final Material rewardMaterial = Material.getMaterial(rewardMaterialName);
            final Material insideMaterial = Material.getMaterial(insideMaterialName);
            final World.Environment environment = World.Environment.valueOf(environmentName);

            final Findable findable = new Findable(insideMaterial, rewardMaterial, environment, minVeinSize, maxVeinSize, minSpawnHeight, maxSpawnHeight, requiredPull, totalPull);
            findables.add(findable);
        }

        Logger.print("Loaded " + findables.size() + " Findable Ores");
    }

    @Override
    public void unload() {}

    /**
     * Returns a Block location for any stored stone locations that match the provided Bukkit Block
     * @param block Bukkit Block
     * @return Block Location
     */
    private BLocatable getStoredStoneLocation(Block block) {
        return placedStone.stream().filter(stone -> stone.getX() == block.getX() && stone.getY() == block.getY() && stone.getZ() == block.getZ() && stone.getWorldName().equals(block.getWorld().getName())).findFirst().orElse(null);
    }

    /**
     * Returns an Immutable List containing all findables possible at the provided block location
     * @param block Block
     * @return Immutable List of Findables
     */
    public ImmutableList<Findable> getPossibleFinds(Block block) {
        return ImmutableList.copyOf(findables.stream().filter(findable -> findable.isObtainable(block)).collect(Collectors.toList()));
    }

    public void run(Player player, Block block) {
        final List<Findable> possibilities = getPossibleFinds(block);

        if (possibilities.isEmpty()) {
            return;
        }

        for (Findable possible : possibilities) {
            if (random.nextInt(possible.getTotalPulls()) > possible.getRequiredPulls()) {
                continue;
            }

            if (spawnVein(possible, player, block)) {
                break;
            }
        }
    }

    /**
     * Handles spawning a vein of the provided Findable for the provided player nearby the provided Block
     * @param findable Findable
     * @param player Player
     * @param block Block
     * @return True if successfully spawned vein
     */
    public boolean spawnVein(Findable findable, Player player, Block block) {
        int size = random.nextInt(findable.maxVeinSize);

        if (size < findable.minVeinSize) {
            size = findable.minVeinSize;
        }

        final List<Block> blocks = Lists.newArrayList();
        int x = block.getX() + random.nextInt(2);
        int y = block.getY() + random.nextInt(2);
        int z = block.getZ() + random.nextInt(2);
        int attempts = 0;

        while (blocks.size() < size && attempts < 50) {
            attempts++;

            final int direction = random.nextInt(6);

            switch (direction) {
                case 1: x++; break;
                case 2: y++; break;
                case 3: z++; break;
                case 4: x--; break;
                case 5: y--; break;
                case 6: z--; break;
            }

            final Block toAdd = block.getWorld().getBlockAt(x, y, z);

            if (!toAdd.getType().equals(findable.getInside())) {
                continue;
            }

            blocks.add(toAdd);
        }

        blocks.forEach(b -> {
            // Worlds.playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK);
            // Worlds.spawnParticle(b.getLocation(), Particle.VILLAGER_HAPPY, 10, 0.5);
            b.setType(findable.getMaterial());
        });

        player.sendMessage(ChatColor.GOLD + "You found " + ChatColor.AQUA + blocks.size() + " " + findable.getName() + ChatColor.GOLD + " nearby");

        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final ItemStack hand = player.getItemInHand();

        if (!isEnabled()) {
            return;
        }

        if (block == null || hand == null) {
            return;
        }

        run(player, block);
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onStoneBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getBlock();
        final BLocatable locatable = getStoredStoneLocation(block);

        if (locatable != null) {
            placedStone.remove(locatable);
        }
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.isCancelled()) {
            return;
        }

        event.getBlocks().forEach(block -> {
            final BLocatable locatable = getStoredStoneLocation(block);

            if (locatable != null) {
                locatable.setX(block.getX());
                locatable.setY(block.getY());
                locatable.setZ(block.getZ());
                locatable.setWorldName(block.getWorld().getName());
            }
        });
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.isCancelled()) {
            return;
        }

        event.getBlocks().forEach(block -> {
            final BLocatable locatable = getStoredStoneLocation(block);

            if (locatable != null) {
                locatable.setX(block.getX());
                locatable.setY(block.getY());
                locatable.setZ(block.getZ());
                locatable.setWorldName(block.getWorld().getName());
            }
        });
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getBlockPlaced();

        if (block == null || !block.getType().equals(Material.STONE)) {
            return;
        }

        placedStone.add(new BLocatable(block));
    }

    @AllArgsConstructor
    public final class Findable {
        @Getter public final Material inside;
        @Getter public final Material material;
        @Getter public final World.Environment environment;
        @Getter public final int minVeinSize;
        @Getter public final int maxVeinSize;
        @Getter public final int minSpawnHeight;
        @Getter public final int maxSpawnHeight;
        @Getter public final int requiredPulls;
        @Getter public final int totalPulls;

        /**
         * Returns a fancy name
         * @return Fancy name
         */
        public String getName() {
            return StringUtils.capitalize(material.name().toLowerCase().replace("_", " "));
        }

        /**
         * Returns true if this findable is obtainable at the provided location
         * @param block Block
         * @return True if findable
         */
        public boolean isObtainable(Block block) {
            if (!block.getType().equals(inside)) {
                return false;
            }

            if (!block.getWorld().getEnvironment().equals(environment)) {
                return false;
            }

            if (block.getY() <= minSpawnHeight || block.getY() >= maxSpawnHeight) {
                return false;
            }

            return true;
        }
    }
}