package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class MagicLootCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("version", "debug", "reload", "generate", "language", "add_effect", "sf_loot", "tools_loot", "set_sf_w");

    private final Plugin plugin;
    private String buildNumber;

    public MagicLootCommand(Plugin plugin) {
        this.plugin = plugin;
        this.buildNumber = loadBuildNumber();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Messages.get("cmd.header",
                    plugin.getDescription().getVersion(), buildNumber));
            sender.sendMessage(Messages.get("cmd.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (!sub.equals("version") && !sender.hasPermission("magicloot3.admin")) {
            sender.sendMessage(Messages.get("cmd.no_permission"));
            return true;
        }

        switch (sub) {
            case "version" -> {
                sender.sendMessage(Messages.get("cmd.header",
                        plugin.getDescription().getVersion(), buildNumber));
                sender.sendMessage(Messages.get("cmd.lang_current", Messages.getCurrentLang()));
                sender.sendMessage(Messages.get(MagicLoot3.isDebug() ? "cmd.debug_on" : "cmd.debug_off"));
            }
            case "debug" -> {
                boolean newState = !MagicLoot3.isDebug();
                MagicLoot3.setDebug(newState);
                sender.sendMessage(Messages.get(newState ? "cmd.debug_on" : "cmd.debug_off"));
            }
            case "reload" -> {
                MagicLoot3.reload(plugin);
                sender.sendMessage(Messages.get("log.config_reloaded",
                        RuinBuilder.ruinNames.size(), RuinBuilder.buildingNames.size()));
            }
            case "language" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("cmd.lang_current", Messages.getCurrentLang()));
                    return true;
                }
                String lang = args[1].toLowerCase();
                if (!Set.of("zh", "en").contains(lang)) {
                    sender.sendMessage(Messages.get("cmd.lang_invalid"));
                    return true;
                }
                MagicLoot3.getInstance().getConfig().set("language", lang);
                MagicLoot3.getInstance().saveConfig();
                Messages.load(MagicLoot3.getInstance(), lang);
                MagicLootConfig.setupConfigs(MagicLoot3.getInstance());
                MagicLootConfig.loadSettings();
                sender.sendMessage(Messages.get("cmd.lang_switched", lang));
            }
            case "generate" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.get("cmd.player_only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("cmd.generate_usage"));
                    sender.sendMessage(Messages.get("cmd.generate_list", allStructureNames()));
                    return true;
                }
                String name = args[1].toLowerCase();
                boolean isBuilding = StructurePlacer.BUILDING_NAMES.contains(name);
                boolean hasName = RuinBuilder.ruinNames.contains(name)
                        || RuinBuilder.buildingNames.contains(name);
                if (!hasName) {
                    sender.sendMessage(Messages.get("cmd.unknown_structure", name));
                    sender.sendMessage(Messages.get("cmd.generate_list", allStructureNames()));
                    return true;
                }
                Location loc = player.getLocation();
                boolean ok = StructurePlacer.place(plugin, loc, name, isBuilding);
                sender.sendMessage(Messages.get(ok
                        ? "log.structure_generated" : "log.structure_failed", name));
            }
            case "add_effect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.get("cmd.player_only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.get("cmd.add_effect_usage"));
                    return true;
                }
                String key = args[1].toLowerCase();
                String pol = null;
                Integer lvl = null;
                if (args.length >= 3) {
                    String p = args[2];
                    if ("+".equals(p) || "-".equals(p)) pol = p;
                    else {
                        try { lvl = Integer.parseInt(p); }
                        catch (NumberFormatException e) {
                            sender.sendMessage(Messages.get("cmd.add_effect_polarity"));
                            return true;
                        }
                    }
                }
                if (args.length >= 4) {
                    try { lvl = Integer.parseInt(args[3]); }
                    catch (NumberFormatException e) {
                        sender.sendMessage(Messages.get("cmd.add_effect_level_format"));
                        return true;
                    }
                }
                ItemManager.addEffectToItem(player, key, pol, lvl);
            }
            case "sf_loot" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.get("cmd.player_only"));
                    return true;
                }
                SfLootGUI.open(player, plugin);
            }
            case "tools_loot" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Messages.get("cmd.player_only"));
                    return true;
                }
                ToolsLootGUI.open(player, plugin);
            }
            case "set_sf_w" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("cmd.set_sf_w_usage"));
                    return true;
                }
                if (!Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
                    sender.sendMessage(Messages.get("cmd.sf_not_enabled"));
                    return true;
                }
                String sfId = args[1].toUpperCase();
                SlimefunItem sfItem = SlimefunItem.getById(sfId);
                if (sfItem == null) {
                    sender.sendMessage(Messages.get("cmd.unknown_sf_item", sfId));
                    return true;
                }
                int weight;
                try {
                    weight = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.get("cmd.weight_must_be_int"));
                    return true;
                }
                if (weight < 0) {
                    sender.sendMessage(Messages.get("cmd.weight_must_be_nonnegative"));
                    return true;
                }
                ConfigManager cfg = new ConfigManager(new File(plugin.getDataFolder(), "Items.yml"));
                cfg.getYaml().set("slimefun." + sfId, weight);
                cfg.save();
                MagicLoot3.reload(plugin);
                sender.sendMessage(Messages.get("cmd.set_sf_w_done", sfId, String.valueOf(weight)));
            }
            default -> sender.sendMessage(Messages.get("log.unknown_command"));
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
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            return List.of("zh", "en");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add_effect")) {
            String prefix = args[1].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String key : ItemManager.potionEffectMap.keySet()) {
                if (key.startsWith(prefix)) matches.add(key);
            }
            return matches;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("set_sf_w")) {
            if (args.length == 2) {
                String prefix = args[1].toUpperCase();
                List<String> matches = new ArrayList<>();
                if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
                    for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                        if (item.getId().startsWith("JEG") || "MAGICLOOT_UNIDENTIFIED".equals(item.getId()) || "MAGICLOOT_PLAYER_HEAD".equals(item.getId())) continue;
                        if (item.getId().startsWith(prefix)) matches.add(item.getId());
                    }
                }
                return matches;
            }
            return List.of();
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
