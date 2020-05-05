package com.playares.humbug;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import com.playares.humbug.cont.mods.MiningMod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

@AllArgsConstructor
@CommandAlias("humbug|hb")
public final class HumbugCommand extends BaseCommand {
    @Getter public final HumbugService plugin;

    @Subcommand("reload")
    @CommandPermission("humbug.reload")
    @Description("Reload Humbug Configuration")
    public void onReload(CommandSender sender) {
        plugin.getModManager().reload();
        sender.sendMessage(ChatColor.GREEN + "All Humbug Mods have been reloaded");
    }

    @Subcommand("mining")
    @CommandPermission("humbug.admin")
    @Description("Run a simulation of the mining system")
    public void onSimulate(CommandSender sender, String simulationCount) {
        int simulations = 10;

        try {
            simulations = Integer.parseInt(simulationCount);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid simulation count, defaulting to 10");
        }

        final Map<MiningMod.Findable, Integer> result = ((MiningMod)plugin.getModManager().getMod(MiningMod.class)).simulate(simulations);
        result.forEach(((findable, integer) -> sender.sendMessage(findable.getName() + ": " + integer)));
    }

    @HelpCommand
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
        sender.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.GOLD + "/" + help.getCommandName() + " help " + (help.getPage() + 1) + ChatColor.YELLOW + " to see the next page");
    }
}