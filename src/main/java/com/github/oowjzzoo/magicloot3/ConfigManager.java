package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {

    private final File file;
    private final YamlConfiguration yaml;

    public ConfigManager(File file) {
        this.file = file;
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public ConfigManager(String path) {
        this(new File(path));
    }

    public void setDefaultValue(String path, Object value) {
        if (!yaml.contains(path)) {
            yaml.set(path, value);
        }
    }

    public int getInt(String path) {
        return yaml.getInt(path);
    }

    public int getInt(String path, int def) {
        return yaml.contains(path) ? yaml.getInt(path) : def;
    }

    public boolean getBoolean(String path) {
        return yaml.getBoolean(path);
    }

    public boolean contains(String path) {
        return yaml.contains(path);
    }

    public List<String> getStringList(String path) {
        return yaml.getStringList(path);
    }

    public YamlConfiguration getYaml() { return yaml; }

    public void setHeader(String header) {
        yaml.options().setHeader(Collections.singletonList(header));
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
