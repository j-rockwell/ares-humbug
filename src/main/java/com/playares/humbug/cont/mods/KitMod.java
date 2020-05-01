package com.playares.humbug.cont.mods;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.playares.humbug.HumbugService;
import com.playares.humbug.cont.HumbugMod;
import com.playares.commons.event.PlayerDamagePlayerEvent;
import com.playares.commons.logger.Logger;
import com.playares.commons.util.general.Configs;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KitMod implements HumbugMod, Listener {
    @Getter public final HumbugService humbug;
    @Getter public final String name = "Kit";
    @Getter @Setter public boolean enabled;
    @Getter public final Set<PotionLimit> potionLimits;
    @Getter public final Set<EnchantLimit> enchantLimits;

    public KitMod(HumbugService humbug) {
        this.humbug = humbug;
        this.potionLimits = Sets.newHashSet();
        this.enchantLimits = Sets.newHashSet();

        humbug.getOwner().registerListener(this);
    }

    @SuppressWarnings("unchecked") @Override
    public void load() {
        if (!enchantLimits.isEmpty()) {
            enchantLimits.clear();
        }

        if (!potionLimits.isEmpty()) {
            potionLimits.clear();
        }

        final YamlConfiguration config = Configs.getConfig(humbug.getOwner(), "humbug");

        this.enabled = config.getBoolean("mods.kit-limits.enabled");

        if (config.get("mods.kit-limits.enchantments") != null) {
            for (String enchantName : config.getConfigurationSection("mods.kit-limits.enchantments").getKeys(false)) {
                final Enchantment enchantment = Enchantment.getByName(enchantName);

                if (enchantment == null) {
                    Logger.error("Skipped enchantment '" + enchantName + "' - Enchantment not found");
                    continue;
                }

                final boolean disabled = config.getBoolean("mods.kit-limits.enchantments." + enchantName + ".disabled");
                final int maxLevel = config.getInt("mods.kit-limits.enchantments." + enchantName + ".max-level");

                final EnchantLimit limit = new EnchantLimit(enchantment, disabled, maxLevel);
                enchantLimits.add(limit);
            }
        }

        if (config.get("mods.kit-limits.banned-potions") != null) {
            for (String potionData : (List<String>)config.getList("mods.kit-limits.banned-potions")) {
                final String[] split = potionData.split(":");

                if (split.length != 2) {
                    Logger.error("Bad potion id " + potionData);
                    continue;
                }

                try {
                    final PotionLimit limit = new PotionLimit(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                    potionLimits.add(limit);
                } catch (NumberFormatException ex) {
                    Logger.error("Bad potion id " + potionData);
                }
            }
        }

        Logger.print("Loaded " + enchantLimits.size() + " Enchantment Limits");
        Logger.print("Loaded " + potionLimits.size() + " Potion Limits");
    }

    @Override
    public void unload() {
        PlayerDamagePlayerEvent.getHandlerList().unregister(this);
        PlayerItemConsumeEvent.getHandlerList().unregister(this);
        PrepareItemEnchantEvent.getHandlerList().unregister(this);
        PotionSplashEvent.getHandlerList().unregister(this);
    }

    /**
     * Returns an Enchant Limit matching the provided Enchantment
     * @param type Enchantment
     * @return Enchant Limit
     */
    public EnchantLimit getEnchantLimit(Enchantment type) {
        return enchantLimits.stream().filter(e -> e.getType().equals(type)).findFirst().orElse(null);
    }

    /**
     * Returns true if the provided ItemStack is a banned potion ID
     * @param item ItemStack
     * @return True if banned
     */
    public boolean isPotionBanned(ItemStack item) {
        return potionLimits.stream().anyMatch(limit -> limit.getItemId() == item.getTypeId() && (short)limit.getItemData() == item.getDurability());
    }

    /**
     * Updates the enchantments of the item for the provided player
     * @param player Player
     * @param item ItemStack
     */
    public void updateEnchantments(Player player, ItemStack item) {
        if (item.getEnchantments().isEmpty()) {
            return;
        }

        final List<Enchantment> toRemove = Lists.newArrayList();
        final Map<Enchantment, Integer> toLower = Maps.newHashMap();

        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            final int level = item.getEnchantmentLevel(enchantment);
            final EnchantLimit limit = getEnchantLimit(enchantment);

            if (limit == null) {
                continue;
            }

            if (limit.isDisabled()) {
                toRemove.add(enchantment);
                continue;
            }

            if (limit.getMaxLevel() < level) {
                toLower.put(enchantment, limit.getMaxLevel());
            }
        }

        toRemove.forEach(removed -> {
            item.removeEnchantment(removed);

            player.sendMessage(ChatColor.DARK_RED + "Removed Enchantment" + ChatColor.RED + ": " +
                    ChatColor.WHITE + StringUtils.capitaliseAllWords(removed.getName().toLowerCase().replace("_", " ")));
        });

        toLower.keySet().forEach(lowered -> {
            final int level = toLower.get(lowered);
            item.addUnsafeEnchantment(lowered, level);

            player.sendMessage(ChatColor.BLUE + "Updated Enchantment" + ChatColor.AQUA + ": " +
                    ChatColor.WHITE + StringUtils.capitaliseAllWords(lowered.getName().toLowerCase().replace("_", " ")));
        });
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            final Player player = (Player)event.getEntity();

            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor == null || armor.getType().equals(Material.AIR)) {
                    continue;
                }

                updateEnchantments(player, armor);
            }
        }

        if (event.getDamager() instanceof Player) {
            final Player player = (Player)event.getDamager();
            final ItemStack item = player.getItemInHand();

            if (item == null || item.getType().equals(Material.AIR)) {
                return;
            }

            updateEnchantments(player, item);
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        final Player player = (Player)event.getEntity();
        final ItemStack item = event.getBow();

        if (item == null || item.getType().equals(Material.AIR)) {
            return;
        }

        updateEnchantments(player, item);
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (!isEnabled()) {
            return;
        }

        final ItemStack item = event.getPotion().getItem();

        if (isPotionBanned(item)) {
            event.setCancelled(true);
            event.getAffectedEntities().clear();

            if (event.getEntity().getShooter() instanceof Player) {
                final Player player = (Player)event.getEntity().getShooter();
                player.sendMessage(ChatColor.RED + "This potion is not allowed");
            }
        }
    }

    @EventHandler
    public void onConsumeItem(PlayerItemConsumeEvent event) {
        if (!isEnabled()) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();

        if (item == null || !item.getType().equals(Material.POTION)) {
            return;
        }

        if (isPotionBanned(item)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "This potion is not allowed");
        }
    }

    @AllArgsConstructor
    public final class EnchantLimit {
        @Getter public final Enchantment type;
        @Getter public final boolean disabled;
        @Getter public final int maxLevel;
    }

    @AllArgsConstructor
    public final class PotionLimit {
        @Getter public final int itemId;
        @Getter public final int itemData;
    }
}
