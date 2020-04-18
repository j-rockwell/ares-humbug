package com.llewkcor.ares.humbug.cont.mods;

import com.llewkcor.ares.commons.event.ProcessedChatEvent;
import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ChatMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "Chat";
    @Getter @Setter public boolean enabled;

    @Getter public boolean hideJoinLeaveMessages;
    @Getter public boolean disablePostingLinks;
    @Getter public List<String> whitelistedLinks;

    public ChatMod(Humbug plugin) {
        this.plugin = plugin;
        this.enabled = false;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void load() {
        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.hideJoinLeaveMessages = config.getBoolean("mods.chat.hide_join_leave_messages");
        this.disablePostingLinks = config.getBoolean("mods.chat.disable_posting_links");
        this.whitelistedLinks = config.getStringList("mods.chat.allowed_links");

        this.enabled = true;
    }

    @Override
    public void unload() {
        this.enabled = false;
    }

    /**
     * Returns true if the provided string is a blacklisted link
     * @param message Message
     * @return True if blacklisted
     */
    private boolean isBlacklistedLink(String message) {
        final boolean match = message.matches("^(http://www\\.|https://www\\.|http://|https://)?[a-z0-9]+([\\-.][a-z0-9]+)*\\.[a-z]{2,5}(:[0-9]{1,5})?(/.*)?$");

        for (String whitelisted : whitelistedLinks) {
            if (message.contains(whitelisted)) {
                return false;
            }
        }

        return match;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isEnabled() && isHideJoinLeaveMessages()) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isEnabled() && isHideJoinLeaveMessages()) {
            event.setQuitMessage(null);
        }
    }

    /**
     * Fixes colored item names breaking death message formatting
     * @param event PlayerDeathEvent
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Player killer = player.getKiller();

        if (killer == null) {
            return;
        }

        final ItemStack hand = killer.getItemInHand();

        if (hand == null || !hand.hasItemMeta() || !hand.getItemMeta().hasDisplayName()) {
            return;
        }

        if (ChatColor.getLastColors(hand.getItemMeta().getDisplayName()).equals(ChatColor.RESET.toString())) {
            return;
        }

        final ItemMeta meta = hand.getItemMeta();
        meta.setDisplayName(meta.getDisplayName() + ChatColor.RESET);
        hand.setItemMeta(meta);
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onChat(ProcessedChatEvent event) {
        if (!isEnabled() || !isDisablePostingLinks() || event.isCancelled()) {
            return;
        }

        final Player player = event.getPlayer();
        final String message = event.getMessage();
        final String[] split = message.split(" ");

        for (String str : split) {
            if (isBlacklistedLink(str)) {

                player.sendMessage(ChatColor.RED + "This type of link is blacklisted for non-premium users. Purchase a rank at " + ChatColor.AQUA +
                        "https://playares.com/store" + ChatColor.RED + " to bypass this restriction");

                event.setCancelled(true);
                return;
            }
        }
    }
}