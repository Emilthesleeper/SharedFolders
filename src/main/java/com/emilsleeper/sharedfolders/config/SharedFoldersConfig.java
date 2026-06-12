package com.emilsleeper.sharedfolders.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.emilsleeper.sharedfolders.StartupCopyManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class SharedFoldersConfig {
    public static final SharedFoldersConfig INSTANCE = new SharedFoldersConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("sharedfolders.json");

    private final Map<String, Boolean> enabledPaths = new HashMap<>();

    private SharedFoldersConfig() {
    }

    public void load() {
        enabledPaths.clear();
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Map<String, Boolean> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) enabledPaths.putAll(loaded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(enabledPaths, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // After saving config, sync enabled paths to .minecraft in background
        StartupCopyManager.startBackgroundApplyReverse();
    }

    public boolean isEnabled(String relativePath) {
        return enabledPaths.getOrDefault(relativePath, false);
    }

    public void setEnabled(String relativePath, boolean enabled) {
        enabledPaths.put(relativePath, enabled);
    }

    public Map<String, Boolean> getEnabledPaths() {
        return enabledPaths;
    }
}