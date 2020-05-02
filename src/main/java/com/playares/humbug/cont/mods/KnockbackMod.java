package com.playares.humbug.cont.mods;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.PacketConstructor;
import com.google.common.collect.Lists;
import com.playares.humbug.HumbugService;
import com.playares.humbug.cont.HumbugMod;
import com.playares.commons.event.PlayerDamagePlayerEvent;
import com.playares.commons.logger.Logger;
import com.playares.commons.util.general.Configs;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class KnockbackMod implements HumbugMod, Listener {
    @Getter public final HumbugService humbug;
    @Getter public final String name = "Knockback";
    @Getter @Setter public boolean enabled;
    @Getter public final List<UUID> recentSprinters;

    @Getter @Setter public double horizontal;
    @Getter @Setter public double vertical;
    @Getter @Setter public double resetSprintModifier;
    @Getter @Setter public double sprintModifier;
    @Getter @Setter public double walkModifier;
    @Getter @Setter public double airModifier;

    public KnockbackMod(HumbugService humbug) {
        this.humbug = humbug;
        this.enabled = false;
        this.recentSprinters = Collections.synchronizedList(Lists.newArrayList());
        this.horizontal = 1.0D;
        this.vertical = 1.0D;

        humbug.getOwner().registerListener(this);
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(humbug.getOwner(), "humbug");

        this.enabled = config.getBoolean("mods.knockback.enabled");
        this.vertical = config.getDouble("mods.knockback.values.vertical");
        this.horizontal = config.getDouble("mods.knockback.values.horizontal");
        this.resetSprintModifier = config.getDouble("mods.knockback.values.movement_modifiers.new_sprint");
        this.sprintModifier = config.getDouble("mods.knockback.values.movement_modifiers.sprint");
        this.walkModifier = config.getDouble("mods.knockback.values.movement_modifiers.walk");
        this.airModifier = config.getDouble("mods.knockback.values.movement_modifiers.air");
    }

    @Override
    public void unload() {
        PlayerVelocityEvent.getHandlerList().unregister(this);
        PlayerDamagePlayerEvent.getHandlerList().unregister(this);
    }

    /**
     * Returns true if this player has recently started sprinting
     * @param player Player
     * @return True if the player has recently sprinted
     */
    private boolean hasRecentlySprinted(Player player) {
        return recentSprinters.contains(player.getUniqueId());
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        final Player player = event.getPlayer();

        if (event.isSprinting()) {
            if (!hasRecentlySprinted(player)) {
                recentSprinters.add(player.getUniqueId());
            }

            return;
        }

        recentSprinters.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();

        if (hasRecentlySprinted(player)) {
            recentSprinters.remove(player.getUniqueId());
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
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

        final double sprintMultiplier = (damager.isSprinting() ? (hasRecentlySprinted(damager) ? resetSprintModifier : sprintModifier) : walkModifier);
        final double enchantMultiplier = (damager.getItemInHand() == null) ? 0 : damager.getItemInHand().getEnchantmentLevel(Enchantment.KNOCKBACK);
        final double airMultiplier = damaged.isOnGround() ? airModifier : (airModifier / 2);

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
        } finally {
            if (hasRecentlySprinted(damager)) {
                recentSprinters.remove(damager.getUniqueId());
            }
        }
    }
}