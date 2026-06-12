package com.emilsleeper.sharedfolders;

import com.emilsleeper.sharedfolders.config.SharedFoldersConfig;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class StartupCopyManager {
    private StartupCopyManager() {
    }

    public static final AtomicBoolean isSyncing = new AtomicBoolean(false);
    public static final AtomicInteger actionsRemaining = new AtomicInteger(0);
    public static volatile String currentAction = "";

    private static final long MAX_BYTES_PER_SECOND = (long) (100L * 1024 * 1024 * 0.80);
    private static final int CHUNK_SIZE = 256 * 1024;

    private static Path trashDir;

    private static WatchService watchService;
    private static Thread watchThread;
    private static final Map<WatchKey, Path> watchKeyMap = new HashMap<>();

    private static Path getSourceDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null) return Paths.get(appdata, ".minecraft");
        return Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft");
    }

    private static Path getTargetDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
    }

    private static Path getTrashDir() {
        if (trashDir == null) {
            String appdata = System.getenv("APPDATA");
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path base = appdata != null
                    ? Paths.get(appdata)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
            trashDir = base.resolve("sharedfolders-trash").resolve(timestamp);
        }
        return trashDir;
    }

    public static void startBackgroundSync() {
        SharedFoldersConfig.INSTANCE.load();
        System.out.println("[SharedFolders] startBackgroundSync called");
        trashDir = null;
        isSyncing.set(true);
        Thread thread = new Thread(() -> {
            try {
                syncEnabledPaths(getSourceDir(), getTargetDir());
            } finally {
                isSyncing.set(false);
                actionsRemaining.set(0);
                currentAction = "Done";
                startObservers();
            }
        }, "SharedFolders-Sync");
        thread.setDaemon(true);
        thread.start();
    }

    public static void startBackgroundApplyReverse() {
        stopObservers();
        trashDir = null;
        isSyncing.set(true);
        Thread thread = new Thread(() -> {
            try {
                syncEnabledPaths(getTargetDir(), getSourceDir());

                syncEnabledPathsNoOverwrite(getSourceDir(), getTargetDir());

            } finally {
                isSyncing.set(false);
                actionsRemaining.set(0);
                currentAction = "Done";
                startObservers();
            }
        }, "SharedFolders-Apply");
        thread.setDaemon(true);
        thread.start();
    }

    public static void startBackgroundApply() {
        startBackgroundSync();
    }

    public static void stopObservers() {
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
        watchKeyMap.clear();
        System.out.println("[SharedFolders] Observers stopped");
    }

    public static void startObservers() {
        stopObservers();

        Map<String, Boolean> config = SharedFoldersConfig.INSTANCE.getEnabledPaths();
        Path gameDir = getTargetDir();

        try {
            watchService = FileSystems.getDefault().newWatchService();

            for (Map.Entry<String, Boolean> entry : config.entrySet()) {
                if (!entry.getValue()) continue;
                Path watchPath = gameDir.resolve(entry.getKey());
                registerRecursive(watchPath);
            }

            System.out.println("[SharedFolders] Observers started, watching "
                    + watchKeyMap.size() + " directories");

        } catch (IOException e) {
            System.err.println("[SharedFolders] Failed to start observers");
            e.printStackTrace();
            return;
        }

        watchThread = new Thread(StartupCopyManager::runWatchLoop, "SharedFolders-Watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private static void registerRecursive(Path root) throws IOException {
        if (!Files.exists(root)) return;

        if (Files.isDirectory(root)) {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    registerDir(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            registerDir(root.getParent());
        }
    }

    private static void registerDir(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchKeyMap.put(key, dir);
    }

    private static void runWatchLoop() {
        Path gameDir = getTargetDir();
        Path appdataMc = getSourceDir();

        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            Path watchedDir = watchKeyMap.get(key);
            if (watchedDir == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path changed = watchedDir.resolve(((WatchEvent<Path>) event).context());
                Path rel = gameDir.relativize(changed);
                Path dest = appdataMc.resolve(rel);

                try {
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        if (Files.exists(dest)) {
                            moveToTrash(dest, appdataMc);
                            System.out.println("[SharedFolders] Watch: trashed " + dest);
                        }
                    } else if (kind == StandardWatchEventKinds.ENTRY_CREATE
                            || kind == StandardWatchEventKinds.ENTRY_MODIFY) {

                        if (Files.isDirectory(changed)) {
                            registerDir(changed);
                            Files.createDirectories(dest);
                        } else if (Files.isRegularFile(changed)) {
                            if (!Files.exists(dest) || !filesAreIdentical(changed, dest)) {
                                Files.createDirectories(dest.getParent());
                                throttledCopy(changed, dest, 0, System.currentTimeMillis());
                                System.out.println("[SharedFolders] Watch: synced " + changed);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[SharedFolders] Watch error on: " + changed);
                    e.printStackTrace();
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyMap.remove(key);
            }
        }

        System.out.println("[SharedFolders] Watch loop exited");
    }

    private static void syncEnabledPaths(Path sourceBase, Path targetBase) {
        Map<String, Boolean> config = SharedFoldersConfig.INSTANCE.getEnabledPaths();
        System.out.println("[SharedFolders] Config entries: " + config.size());

        List<Path[]> copyJobs = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : config.entrySet()) {
            if (!entry.getValue()) continue;
            Path sourcePath = sourceBase.resolve(entry.getKey());
            if (!Files.exists(sourcePath)) continue;

            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> walk = Files.walk(sourcePath)) {
                    walk.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                        Path rel = sourceBase.relativize(p);
                        copyJobs.add(new Path[]{p, targetBase.resolve(rel)});
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Path rel = sourceBase.relativize(sourcePath);
                copyJobs.add(new Path[]{sourcePath, targetBase.resolve(rel)});
            }
        }

        actionsRemaining.set(copyJobs.size());

        long bytesThisSecond = 0;
        long windowStart = System.currentTimeMillis();

        for (Path[] job : copyJobs) {
            Path src = job[0];
            Path dst = job[1];
            currentAction = src.getFileName().toString();
            try {
                if (Files.exists(dst) && filesAreIdentical(src, dst)) {
                    actionsRemaining.decrementAndGet();
                    continue;
                }
                Files.createDirectories(dst.getParent());
                bytesThisSecond = throttledCopy(src, dst, bytesThisSecond, windowStart);
                long now = System.currentTimeMillis();
                if (now - windowStart >= 1000) {
                    windowStart = now;
                    bytesThisSecond = 0;
                }
            } catch (IOException e) {
                System.err.println("[SharedFolders] Failed to copy: " + src);
                e.printStackTrace();
            }
            actionsRemaining.decrementAndGet();
        }
    }



    private static void moveToTrash(Path file, Path base) throws IOException {
        Path rel = base.relativize(file);
        Path trashDest = getTrashDir().resolve(rel);
        Files.createDirectories(trashDest.getParent());
        Files.move(file, trashDest, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteEmptyParents(Path dir, Path stopAt) {
        try {
            while (dir != null && !dir.equals(stopAt)) {
                try (Stream<Path> contents = Files.list(dir)) {
                    if (contents.findAny().isPresent()) break;
                }
                Files.delete(dir);
                dir = dir.getParent();
            }
        } catch (IOException ignored) {
        }
    }

    private static long throttledCopy(Path src, Path dst, long bytesThisSecond,
                                      long windowStart) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(src));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(dst,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytesThisSecond += read;
                if (bytesThisSecond >= MAX_BYTES_PER_SECOND) {
                    long elapsed = System.currentTimeMillis() - windowStart;
                    if (elapsed < 1000) {
                        try {
                            Thread.sleep(1000 - elapsed);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    bytesThisSecond = 0;
                }
            }
        }
        return bytesThisSecond;
    }

    private static boolean filesAreIdentical(Path a, Path b) {
        try {
            if (Files.size(a) != Files.size(b)) return false;
            return sha256(a).equals(sha256(b));
        } catch (IOException e) {
            return false;
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buf = new byte[CHUNK_SIZE];
                int read;
                while ((read = in.read(buf)) != -1) {
                    digest.update(buf, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static void syncEnabledPathsNoOverwrite(Path sourceBase, Path targetBase) {
        Map<String, Boolean> config = SharedFoldersConfig.INSTANCE.getEnabledPaths();

        List<Path[]> copyJobs = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : config.entrySet()) {
            if (!entry.getValue()) continue;
            Path sourcePath = sourceBase.resolve(entry.getKey());
            if (!Files.exists(sourcePath)) continue;

            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> walk = Files.walk(sourcePath)) {
                    walk.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                        Path rel = sourceBase.relativize(p);
                        Path dst = targetBase.resolve(rel);
                        if (!Files.exists(dst)) {
                            copyJobs.add(new Path[]{p, dst});
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Path rel = sourceBase.relativize(sourcePath);
                Path dst = targetBase.resolve(rel);
                if (!Files.exists(dst)) {
                    copyJobs.add(new Path[]{sourcePath, dst});
                }
            }
        }

        actionsRemaining.set(copyJobs.size());

        long bytesThisSecond = 0;
        long windowStart = System.currentTimeMillis();

        for (Path[] job : copyJobs) {
            Path src = job[0];
            Path dst = job[1];
            currentAction = src.getFileName().toString();
            try {
                Files.createDirectories(dst.getParent());
                bytesThisSecond = throttledCopy(src, dst, bytesThisSecond, windowStart);
                long now = System.currentTimeMillis();
                if (now - windowStart >= 1000) {
                    windowStart = now;
                    bytesThisSecond = 0;
                }
            } catch (IOException e) {
                System.err.println("[SharedFolders] Failed to copy (no-overwrite): " + src);
                e.printStackTrace();
            }
            actionsRemaining.decrementAndGet();
        }
    }
}