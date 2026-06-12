package com.emilsleeper.sharedfolders.client;

import com.emilsleeper.sharedfolders.config.YaclConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class SharedFoldersModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return YaclConfigScreen::create;
    }
}