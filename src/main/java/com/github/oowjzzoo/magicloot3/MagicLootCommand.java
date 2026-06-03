package com.github.oowjzzoo.magicloot3;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class MagicLootCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("version", "debug", "reload", "generate");

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
            sender.sendMessage(ChatColor.GRAY + "/magicloot version|debug|reload|generate <name>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // Non-version commands require admin
        if (!sub.equals("version") && !sender.hasPermission("magicloot3.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission for that.");
            return true;
        }

        switch (sub) {
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
            case "reload" -> {
                MagicLoot3.reload(plugin);
                sender.sendMessage(ChatColor.GOLD + "MagicLoot3 config reloaded!");
            }
            case "generate" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use generate.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /magicloot generate <name>");
                    sender.sendMessage(ChatColor.GRAY + "Available: " + allStructureNames());
                    return true;
                }
                String name = args[1].toLowerCase();
                boolean isBuilding = StructurePlacer.BUILDING_NAMES.contains(name);
                boolean hasName = RuinBuilder.ruinNames.contains(name)
                        || RuinBuilder.buildingNames.contains(name);
                if (!hasName) {
                    sender.sendMessage(ChatColor.RED + "Unknown structure: " + name);
                    sender.sendMessage(ChatColor.GRAY + "Available: " + allStructureNames());
                    return true;
                }
                Location loc = player.getLocation();
                boolean ok = StructurePlacer.place(plugin, loc, name, isBuilding);
                sender.sendMessage(ok
                        ? ChatColor.GREEN + "Generated " + name + " at your location."
                        : ChatColor.RED + "Failed to generate " + name + ".");
            }
            default -> sender.sendMessage(ChatColor.RED
                    + "Unknown. Use: /magicloot version|debug|reload|generate <name>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) matches.add(sub);
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            String prefix = args[1].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String name : allStructureNamesList()) {
                if (name.startsWith(prefix)) matches.add(name);
            }
            return matches;
        }
        return List.of();
    }

    private String allStructureNames() {
        List<String> all = allStructureNamesList();
        return all.isEmpty() ? "(none)" : String.join(", ", all);
    }

    private List<String> allStructureNamesList() {
        List<String> all = new ArrayList<>();
        all.addAll(RuinBuilder.ruinNames);
        all.addAll(RuinBuilder.buildingNames);
        return all;
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
