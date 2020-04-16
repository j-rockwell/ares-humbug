package com.llewkcor.ares.humbug.cont.mods;

import com.google.common.collect.ImmutableMap;
import com.llewkcor.ares.commons.util.bukkit.Scheduler;
import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

public final class PotionMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "Potions";
    @Getter @Setter public boolean enabled;

    private final Map<Material, Double> baseDamageValues = ImmutableMap.<Material, Double> builder()
            // Swords
            .put(Material.WOOD_SWORD, 5.0).put(Material.GOLD_SWORD, 5.0).put(Material.STONE_SWORD, 6.0)
            .put(Material.IRON_SWORD, 7.0).put(Material.DIAMOND_SWORD, 8.0)

            // Axes
            .put(Material.WOOD_AXE, 4.0).put(Material.GOLD_AXE, 4.0).put(Material.STONE_AXE, 5.0)
            .put(Material.IRON_AXE, 6.0).put(Material.DIAMOND_AXE, 7.0)

            // Pickaxes
            .put(Material.WOOD_PICKAXE, 3.0).put(Material.GOLD_PICKAXE, 3.0).put(Material.STONE_PICKAXE, 4.0)
            .put(Material.IRON_PICKAXE, 5.0).put(Material.DIAMOND_PICKAXE, 6.0)

            // Shovels
            .put(Material.WOOD_SPADE, 2.0).put(Material.GOLD_SPADE, 2.0).put(Material.STONE_SPADE, 3.0)
            .put(Material.IRON_SPADE, 4.0).put(Material.DIAMOND_SPADE, 5.0).build();

    private final Map<Material, Double> criticalDamageValues = ImmutableMap.<Material, Double> builder()
            // Swords
            .put(Material.WOOD_SWORD, 7.5).put(Material.GOLD_SWORD, 7.5).put(Material.STONE_SWORD, 9.0)
            .put(Material.IRON_SWORD, 10.5).put(Material.DIAMOND_SWORD, 12.0)

            // Axes
            .put(Material.WOOD_AXE, 6.0).put(Material.GOLD_AXE, 6.0).put(Material.STONE_AXE, 7.5)
            .put(Material.IRON_AXE, 9.0).put(Material.DIAMOND_AXE, 10.5)

            // Pickaxes
            .put(Material.WOOD_PICKAXE, 4.5).put(Material.GOLD_PICKAXE, 4.5).put(Material.STONE_PICKAXE, 6.0)
            .put(Material.IRON_PICKAXE, 7.5).put(Material.DIAMOND_PICKAXE, 9.0)

            // Shovels
            .put(Material.WOOD_SPADE, 3.0).put(Material.GOLD_SPADE, 3.0).put(Material.STONE_SPADE, 4.5)
            .put(Material.IRON_SPADE, 6.0).put(Material.DIAMOND_SPADE, 7.5).build();

    @Getter public boolean oldHealthEnabled;
    @Getter public boolean oldRegenEnabled;
    @Getter public boolean oldStrengthEnabled;

    public PotionMod(Humbug plugin) {
        this.plugin = plugin;
        this.enabled = false;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.enabled = config.getBoolean("mods.potions.enabled");
        this.oldHealthEnabled = config.getBoolean("mods.potions.old_health");
        this.oldRegenEnabled = config.getBoolean("mods.potions.old_regen");
        this.oldStrengthEnabled = config.getBoolean("mods.potions.old_strength");
    }

    @Override
    public void unload() {

    }

    private double calculateFinalDamage(Player player) {
        final ItemStack hand = player.getItemInHand();
        final double base = (hand != null ? (!player.isOnGround() && player.getVelocity().getY() < 0.0) ? criticalDamageValues.getOrDefault(hand.getType(), 1.0) : baseDamageValues.getOrDefault(hand.getType(), 1.0) : 1.0);
        double strength = 0.0;
        double weakness = 0.0;
        double sharpness = 0.0;
        double result;

        if (hand != null && hand.hasItemMeta() && hand.getItemMeta().hasEnchant(Enchantment.DAMAGE_ALL)) {
            sharpness = hand.getEnchantmentLevel(Enchantment.DAMAGE_ALL) * 1.25;
        }

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.INCREASE_DAMAGE)) {
                strength = 3 << effect.getAmplifier();
            }

            if (effect.getType().equals(PotionEffectType.WEAKNESS)) {
                weakness = (effect.getAmplifier() + 1) * -0.5;
            }
        }

        result = base;
        result += sharpness;
        result += strength;
        result += weakness;

        return result;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!isEnabled() || !isOldStrengthEnabled()) {
            return;
        }

        final Entity damager = event.getDamager();

        if (!(damager instanceof Player)) {
            return;
        }

        event.setDamage(calculateFinalDamage((Player)damager));
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!isEnabled() || (!isOldRegenEnabled() && !isOldHealthEnabled())) {
            return;
        }

        final LivingEntity entity = (LivingEntity)event.getEntity();
        int level = 0;

        for (PotionEffect effect : entity.getActivePotionEffects()) {
            final PotionEffectType type = effect.getType();
            final int amplifier = effect.getAmplifier();

            if (type.equals(PotionEffectType.REGENERATION) || type.equals(PotionEffectType.HEAL)) {
                level = amplifier + 1;
                break;
            }
        }

        final EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        final double amount = event.getAmount();

        if (isOldHealthEnabled() && reason.equals(EntityRegainHealthEvent.RegainReason.MAGIC) && amount > 1.0 && level >= 0) {
            event.setAmount(amount * 1.5);
            return;
        }

        if (isOldRegenEnabled() && reason.equals(EntityRegainHealthEvent.RegainReason.MAGIC_REGEN) && amount == 1.0 && level > 0) {
            new Scheduler(plugin).sync(() -> {
                if (entity.isDead()) {
                    return;
                }

                final double max = entity.getMaxHealth();
                final double current = entity.getHealth();

                if (max >= current) {
                    return;
                }

                entity.setHealth((max >= current + 1.0) ? current + 1.0 : max);
            }).delay(50L / (level * 2)).run();
        }
    }
}
