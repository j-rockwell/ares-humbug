package com.playares.humbug.cont.mods;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Description;
import com.google.common.collect.Lists;
import com.playares.commons.logger.Logger;
import com.playares.humbug.HumbugService;
import com.playares.humbug.cont.HumbugMod;
import com.playares.commons.item.ItemBuilder;
import com.playares.commons.util.general.Configs;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;

public final class XPMod implements HumbugMod, Listener {
    @Getter public final HumbugService humbug;
    @Getter public final String name = "Experience";
    @Getter @Setter public boolean enabled;
    @Getter @Setter public boolean initialized;

    @Getter @Setter public double baseExpMultiplier;
    @Getter @Setter public boolean bottleExpEnabled;
    @Getter @Setter public boolean lootingFortuneMultiplierEnabled;
    @Getter @Setter public boolean preventBreakingWhitelistedSpawnersEnabled;
    @Getter public List<String> whitelistedSpawnerTypes;

    public XPMod(HumbugService humbug) {
        this.humbug = humbug;
        this.enabled = false;
        this.initialized = false;
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(humbug.getOwner(), "humbug");

        this.enabled = true;
        this.bottleExpEnabled = config.getBoolean("mods.xp.bottle_exp_enabled");
        this.baseExpMultiplier = config.getDouble("mods.xp.exp_base_multiplier");
        this.lootingFortuneMultiplierEnabled = config.getBoolean("mods.xp.looting_fortune_multiplier_enabled");
        this.preventBreakingWhitelistedSpawnersEnabled = config.getBoolean("mods.xp.monster_spawners.prevent_breaking_whitelisted_spawners");
        this.whitelistedSpawnerTypes = config.getStringList("mods.xp.monster_spawners.whitelisted_spawners");

        if (initialized) {
            return;
        }

        final ItemStack expBottle = new ItemStack(Material.EXP_BOTTLE);
        final ShapedRecipe recipe = new ShapedRecipe(expBottle);

        recipe.shape("*");
        recipe.setIngredient('*', Material.EMERALD);

        humbug.getOwner().getServer().addRecipe(recipe);
        humbug.getOwner().registerListener(this);
        humbug.getOwner().registerCommand(new BottleCommand());

        this.initialized = true;
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!isEnabled() || !isBottleExpEnabled() || event.isCancelled()) {
            return;
        }

        final Projectile projectile = event.getEntity();
        final ProjectileSource shooter = event.getEntity().getShooter();

        if (!(projectile instanceof ThrownExpBottle) || !(shooter instanceof Player)) {
            return;
        }

        final Player player = (Player)shooter;
        final ItemStack bottle =  player.getItemInHand();

        if (bottle == null || !bottle.getType().equals(Material.EXP_BOTTLE)) {
            return;
        }

        final ItemMeta meta = bottle.getItemMeta();
        final List<String> lore = meta.getLore();

        if (lore == null || lore.size() != 1) {
            return;
        }

        // 345 exp
        final String line = lore.get(0);
        final String expNumber = ChatColor.stripColor(line.replace(" exp", ""));
        int exp = 0;

        try {
            exp = Integer.parseInt(expNumber);
        } catch (NumberFormatException ex) {
            return;
        }

        event.setCancelled(true);

        if (bottle.getAmount() <= 1) {
            player.getInventory().removeItem(bottle);
        } else {
            bottle.setAmount(bottle.getAmount() - 1);
        }

        setTotalExperience(player, (exp + getTotalExperience(player)));
        player.sendMessage(ChatColor.GREEN + "You consumed " + ChatColor.AQUA + exp + " experience");
    }

    @Override
    public void unload() {}

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled() || !isLootingFortuneMultiplierEnabled() || event.isCancelled()) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack hand = player.getItemInHand();

        if (hand == null || !hand.containsEnchantment(Enchantment.LOOT_BONUS_BLOCKS)) {
            return;
        }

        final int enchantMultiplier = hand.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        final int xp = event.getExpToDrop();

        event.setExpToDrop((int)Math.round((xp * (enchantMultiplier * baseExpMultiplier))));
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isEnabled() || !isLootingFortuneMultiplierEnabled()) {
            return;
        }
        
        final LivingEntity entity = event.getEntity();
        final Player killer = entity.getKiller();

        if (killer == null) {
            return;
        }

        final ItemStack hand = killer.getItemInHand();

        if (hand == null || !hand.containsEnchantment(Enchantment.LOOT_BONUS_BLOCKS)) {
            return;
        }

        final int enchantMultiplier = hand.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        final int xp = event.getDroppedExp();

        event.setDroppedExp((int)Math.round((xp * (enchantMultiplier * baseExpMultiplier))));
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isEnabled() || event.isCancelled()) {
            return;
        }

        if (event.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER) &&
                !whitelistedSpawnerTypes.contains(event.getEntityType().name())) {

            event.setCancelled(true);

        }
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (!isEnabled() || !isPreventBreakingWhitelistedSpawnersEnabled() || event.isCancelled()) {
            return;
        }

        final Block block = event.getBlock();

        if (block == null || !block.getType().equals(Material.MOB_SPAWNER)) {
            return;
        }

        final CreatureSpawner spawner = (CreatureSpawner)block.getState();

        if (whitelistedSpawnerTypes.contains(spawner.getSpawnedType().name()) && !event.getPlayer().hasPermission("humbug.bypass")) {
            event.getPlayer().sendMessage(ChatColor.RED + "This type of monster spawner can not be broken");
            event.setCancelled(true);
        }
    }

    /**
     * Sets the total experience for the provided Player
     * @param player Player
     * @param exp Experience
     */
    public static void setTotalExperience(final Player player, final int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("Experience is negative!");
        }

        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);

        int amount = exp;
        while (amount > 0) {
            final int expToLevel = getExpAtLevel(player.getLevel());
            amount -= expToLevel;

            if (amount >= 0) {
                // give until next level
                player.giveExp(expToLevel);
            }
            else {
                // give the rest
                amount += expToLevel;
                player.giveExp(amount);
                amount = 0;
            }
        }
    }

    /**
     * Returns the EXP for the provided Minecraft EXP Level
     * @param level EXP Level
     * @return EXP at Level
     */
    public  static  int getExpAtLevel(final int level) {
        if (level <= 15) {
            return (2*level) + 7;
        }

        if (level <= 30) {
            return (5*level) -38;
        }

        return (9*level)-158;

    }

    /**
     * Returns the total experience for the provided Player
     * @param player Player
     * @return Total Experience Points
     */
    public static int getTotalExperience(final Player player) {
        int exp = Math.round(getExpAtLevel(player.getLevel()) * player.getExp());
        int currentLevel = player.getLevel();

        while (currentLevel > 0) {
            currentLevel--;
            exp += getExpAtLevel(currentLevel);
        }

        if (exp < 0) {
            exp = Integer.MAX_VALUE;
        }

        return exp;
    }

    public final class BottleCommand extends BaseCommand {
        @CommandAlias("bottle")
        @Description("Bottle all of your current EXP")
        public void onBottle(Player player) {
            final int experience = XPMod.getTotalExperience(player);
            final ItemStack hand = player.getItemInHand();

            if (!isBottleExpEnabled()) {
                player.sendMessage(ChatColor.RED + "Bottling Experience has been disabled");
                return;
            }

            if (!player.hasPermission("humbug.exp.bottle")) {
                player.sendMessage(ChatColor.RED + "This feature is only allowed for premium users.");
                player.sendMessage(ChatColor.YELLOW + "To bypass this limitation purchase a rank at " + ChatColor.AQUA + "https://playares.com/store");
                return;
            }

            if (experience <= 0) {
                player.sendMessage(ChatColor.RED + "You do not have any experience to bottle");
                return;
            }

            if (hand == null || !hand.getType().equals(Material.GLASS_BOTTLE)) {
                player.sendMessage(ChatColor.RED + "You are not holding an empty glass bottle");
                return;
            }

            final ItemStack item = new ItemBuilder()
                    .setMaterial(Material.EXP_BOTTLE)
                    .addLore(ChatColor.DARK_PURPLE + "" + experience + " exp")
                    .build();

            if (hand.getAmount() <= 1) {
                player.getInventory().removeItem(hand);
            } else {
                hand.setAmount(hand.getAmount() - 1);
            }

            player.updateInventory();
            player.setLevel(0);
            player.setExp(0);
            player.setTotalExperience(0);
            player.sendMessage(ChatColor.GREEN + "You have bottled " + experience + " experience");

            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), item);
                player.sendMessage(ChatColor.RED + "The Experience Bottle has dropped at your feet because your inventory is full");
            } else {
                player.getInventory().addItem(item);
            }
        }
    }
}