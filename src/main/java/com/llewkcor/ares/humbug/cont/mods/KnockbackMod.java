package com.llewkcor.ares.humbug.cont.mods;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.PacketConstructor;
import com.llewkcor.ares.commons.event.PlayerDamagePlayerEvent;
import com.llewkcor.ares.commons.logger.Logger;
import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;

public final class KnockbackMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "Knockback";
    @Getter @Setter public boolean enabled;

    @Getter @Setter public double horizontal;
    @Getter @Setter public double vertical;

    public KnockbackMod(Humbug plugin) {
        this.plugin = plugin;
        this.enabled = false;
        this.horizontal = 1.0D;
        this.vertical = 1.0D;
    }

    @Override
    public void load() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.enabled = config.getBoolean("mods.knockback.enabled");
        this.vertical = config.getDouble("mods.knockback.values.vertical");
        this.horizontal = config.getDouble("mods.knockback.values.horizontal");
    }

    @Override
    public void unload() {
        PlayerVelocityEvent.getHandlerList().unregister(this);
        PlayerDamagePlayerEvent.getHandlerList().unregister(this);
    }

    @EventHandler
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (!isEnabled() || event.isCancelled()) {
            return;
        }

        final Player player = event.getPlayer();
        final EntityDamageEvent lastDamage = player.getLastDamageCause();

        if (!(lastDamage instanceof EntityDamageByEntityEvent)) {
            return;
        }

        final EntityDamageByEntityEvent entityDamageEvent = (EntityDamageByEntityEvent)lastDamage;

        if (entityDamageEvent.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerDamagePlayer(PlayerDamagePlayerEvent event) {
        if (!isEnabled() || event.isCancelled()) {
            return;
        }

        final Player damaged = event.getDamaged();
        final Player damager = event.getDamager();

        if (damaged.getNoDamageTicks() > damaged.getMaximumNoDamageTicks() / 2D) {
            return;
        }

        final double sprintMultiplier = (damager.isSprinting() ? 0.8D : 0.5D);
        final double enchantMultiplier = (damager.getItemInHand() == null) ? 0 : damager.getItemInHand().getEnchantmentLevel(Enchantment.KNOCKBACK);
        final double airMultiplier = damaged.isOnGround() ? 1 : 0.5;

        final Vector velocity = damaged.getLocation().toVector().subtract(damager.getLocation().toVector()).normalize();

        velocity.setX((velocity.getX() * sprintMultiplier + enchantMultiplier) * horizontal);
        velocity.setY(0.35D * airMultiplier * vertical);
        velocity.setZ((velocity.getZ() * sprintMultiplier + enchantMultiplier) * horizontal);

        final PacketConstructor constructor = ProtocolLibrary.getProtocolManager().createPacketConstructor(PacketType.Play.Server.ENTITY_VELOCITY, 0, (double)0, (double)0, (double)0);
        final PacketContainer container = constructor.createPacket(damaged.getEntityId(), velocity.getX(), velocity.getY(), velocity.getZ());

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(damaged, container);
        } catch (InvocationTargetException e) {
            Logger.error("Failed to send velocity packet");
        }
    }
}