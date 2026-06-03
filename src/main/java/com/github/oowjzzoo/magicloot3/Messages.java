package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class Messages {

    private static YamlConfiguration lang;
    private static String currentLang = "zh";

    private Messages() {}

    public static void load(Plugin plugin, String langCode) {
        currentLang = langCode;
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        File file = new File(langDir, langCode + ".yml");
        if (!file.exists()) {
            try (InputStream in = Messages.class.getResourceAsStream("/lang/" + langCode + ".yml")) {
                if (in != null) Files.copy(in, file.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract lang file: " + langCode);
            }
        }
        lang = YamlConfiguration.loadConfiguration(file);
    }

    public static String get(String key, Object... args) {
        String msg = lang.getString(key, key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static List<String> getList(String key) {
        return lang.getStringList(key);
    }

    public static String getCurrentLang() {
        return currentLang;
    }
}
