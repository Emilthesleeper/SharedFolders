package com.emilsleeper.sharedfolders.client.hud;

import com.emilsleeper.sharedfolders.StartupCopyManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class SyncHudOverlay implements HudElement {

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("sharedfolders", "sync_status"),
                new SyncHudOverlay()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
        if (!StartupCopyManager.isSyncing.get()) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getWidth();

        guiGraphicsExtractor.fill(10, 10, 170, 50, 0xAA000000);
        guiGraphicsExtractor.text(mc.font, "HUD TEST", 14, 14, 0xFFFFFF00, true);

        int remaining = StartupCopyManager.actionsRemaining.get();
        String line1 = "Syncing files...";
        String line2 = remaining + " files remaining";
        String line3 = StartupCopyManager.currentAction;
        String truncated = line3.length() > 22 ? line3.substring(0, 22) + "..." : line3;

        int padding = 4;
        int boxWidth = 160;
        int x = screenWidth - boxWidth - padding;
        int y = padding;


        guiGraphicsExtractor.fill(x - 2, y - 2, x + boxWidth + 2, y + 42, 0xAA000000);
        guiGraphicsExtractor.text(mc.font, line1,     x, y,      0xFFFFAA00, true);
        guiGraphicsExtractor.text(mc.font, line2,     x, y + 11, 0xFFFFFFFF, true);
        guiGraphicsExtractor.text(mc.font, truncated, x, y + 22, 0xFFAAAAAA, true);
    }
}