package com.emilsleeper.sharedfolders.client;

import com.emilsleeper.sharedfolders.client.hud.SyncHudOverlay;
import net.fabricmc.api.ClientModInitializer;

public class SharedfoldersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SyncHudOverlay.register();
    }
}