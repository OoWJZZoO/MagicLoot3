package com.github.oowjzzoo.magicloot3;

import java.io.InputStream;
import java.util.Properties;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class MagicLootCommand implements CommandExecutor {

    private final Plugin plugin;
    private String buildNumber;

    public MagicLootCommand(Plugin plugin) {
        this.plugin = plugin;
        this.buildNumber = loadBuildNumber();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "MagicLoot3 " + ChatColor.GRAY + "v"
                    + plugin.getDescription().getVersion() + " (build #" + buildNumber + ")");
            sender.sendMessage(ChatColor.GRAY + "Usage: /magicloot <version|debug>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "version" -> {
                sender.sendMessage(ChatColor.GOLD + "MagicLoot3 " + ChatColor.GRAY
                        + "v" + plugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.GRAY + "Build: #" + buildNumber);
                sender.sendMessage(ChatColor.GRAY + "Structure mode: "
                        + ChatColor.GREEN + "vanilla (StructureManager)");
                sender.sendMessage(ChatColor.GRAY + "Debug mode: "
                        + (MagicLoot3.isDebug() ? ChatColor.GREEN + "on" : ChatColor.RED + "off"));
            }
            case "debug" -> {
                boolean newState = !MagicLoot3.isDebug();
                MagicLoot3.setDebug(newState);
                sender.sendMessage(ChatColor.GOLD + "MagicLoot3 debug mode: "
                        + (newState ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use: /magicloot <version|debug>");
        }
        return true;
    }

    private String loadBuildNumber() {
        try (InputStream in = getClass().getResourceAsStream("/build-info.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                return props.getProperty("build", "0");
            }
        } catch (Exception ignored) {}
        return "0";
    }
}
