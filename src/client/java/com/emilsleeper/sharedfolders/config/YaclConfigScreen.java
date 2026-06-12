package com.emilsleeper.sharedfolders.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public final class YaclConfigScreen {
    private YaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SharedFoldersConfig.INSTANCE.load();

        List<InstanceScanner.PathEntry> allEntries = InstanceScanner.scanTopLevel();
        List<InstanceScanner.PathEntry> folders = allEntries.stream().filter(InstanceScanner.PathEntry::directory).toList();
        if (folders.contains(new InstanceScanner.PathEntry(null, ".fabric", ".fabric", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals(".fabric")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, ".mixin.out", ".mixin.out", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals(".mixin.out")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, ".replay_cache", ".replay_cache", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals(".replay_cache")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, "logs", "logs", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals("logs")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, "debug", "debug", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals("debug")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, "data", "data", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals("data")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, "crash-reports", "crash-reports", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals("crash-reports")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, "config", "config", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals("config")).toList();
        }
        if (folders.contains(new InstanceScanner.PathEntry(null, "cache", "cache", true))) {
            folders = folders.stream().filter(entry -> !entry.relativePath().equals("cache")).toList();
        }
        List<InstanceScanner.PathEntry> files = allEntries.stream().filter(entry -> !entry.directory()).toList();
        if (files.contains(new InstanceScanner.PathEntry(null, "servers.dat_old", "servers.dat_old", false))) {
            files = files.stream().filter(entry -> !entry.relativePath().equals("servers.dat_old")).toList();
        }
        if (files.contains(new InstanceScanner.PathEntry(null, "debug-profile.json", "debug-profile.json", false))) {
            files = files.stream().filter(entry -> !entry.relativePath().equals("debug-profile.json")).toList();
        }
        if (files.contains(new InstanceScanner.PathEntry(null, "icon.png", "icon.png", false))) {
            files = files.stream().filter(entry -> !entry.relativePath().equals("icon.png")).toList();
        }
        if (files.contains(new InstanceScanner.PathEntry(null, "realms_persistence.json", "realms_persistence.json", false))) {
            files = files.stream().filter(entry -> !entry.relativePath().equals("realms_persistence.json")).toList();
        }

        OptionGroup.Builder foldersGroup = OptionGroup.createBuilder()
                .name(Component.literal("Folders"));

        for (InstanceScanner.PathEntry entry : folders) {
            foldersGroup.option(buildPathOption(entry));
        }

        OptionGroup.Builder filesGroup = OptionGroup.createBuilder()
                .name(Component.literal("Files"));

        for (InstanceScanner.PathEntry entry : files) {
            filesGroup.option(buildPathOption(entry));
        }

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Shared Folders"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Folders"))
                        .group(foldersGroup.build())
                        .group(filesGroup.build())
                        .build())
                .save(() -> {
                    SharedFoldersConfig.INSTANCE.save();
                    applySelectedPaths(SharedFoldersConfig.INSTANCE.getEnabledPaths());
                })
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> buildPathOption(InstanceScanner.PathEntry entry) {
        String relativePath = entry.relativePath();
        String icon = entry.directory() ? "📁 " : "📄 ";

        return Option.<Boolean>createBuilder()
                .name(Component.literal(icon + entry.name()))
                .description(OptionDescription.of(Component.literal(relativePath)))
                .binding(
                        false,
                        () -> SharedFoldersConfig.INSTANCE.isEnabled(relativePath),
                        newValue -> SharedFoldersConfig.INSTANCE.setEnabled(relativePath, newValue)
                )
                .controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter())
                .build();
    }

    private static void applySelectedPaths(Map<String, Boolean> enabledPaths) {
        enabledPaths.forEach((path, enabled) -> {
            System.out.println((enabled ? "[ENABLED] " : "[DISABLED] ") + path);
        });
    }
}