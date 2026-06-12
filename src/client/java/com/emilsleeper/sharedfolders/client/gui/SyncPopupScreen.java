package com.emilsleeper.sharedfolders.client.gui;

import com.emilsleeper.sharedfolders.StartupCopyManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class SyncPopupScreen extends Screen {

    private final Screen parent;

    public SyncPopupScreen(Screen parent) {
        super(Component.literal("Sync Shared Folders"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Yes button
        this.addRenderableWidget(Button.builder(
                Component.literal("Yes"),
                button -> {
                    System.out.println("[SharedFolders] Yes clicked");
                    StartupCopyManager.startBackgroundSync();
                    this.minecraft.setScreen(parent);
                }
        ).bounds(centerX - 105, centerY + 10, 100, 20).build());

        // No button
        this.addRenderableWidget(Button.builder(
                Component.literal("No"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 5, centerY + 10, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Draw the question text centered above the buttons
        graphics.text(
                this.font,
                "Sync files from .minecraft?",
                this.width / 2 - this.font.width("Sync files from .minecraft?") / 2,
                this.height / 2 - 20,
                0xFFFFFF,
                true
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}