package com.emilsleeper.sharedfolders.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class InstanceScanner {
    private InstanceScanner() {
    }

    public static List<PathEntry> scanTopLevel() {
        Path gameDir = FabricLoader.getInstance().getGameDir();

        try (Stream<Path> stream = Files.list(gameDir)) {
            return stream
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(path -> new PathEntry(
                            path,
                            gameDir.relativize(path).toString().replace("\\", "/"),
                            path.getFileName().toString(),
                            Files.isDirectory(path)
                    ))
                    .toList();
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public record PathEntry(Path absolutePath, String relativePath, String name, boolean directory) {
    }
}