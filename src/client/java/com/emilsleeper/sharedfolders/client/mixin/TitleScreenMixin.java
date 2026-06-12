package com.emilsleeper.sharedfolders.client.mixin;

import com.emilsleeper.sharedfolders.client.gui.SyncPopupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    private static boolean hasShownPopup = false;

    @Inject(at = @At("TAIL"), method = "init")
    private void onInit(CallbackInfo ci) {
        if (hasShownPopup) return;
        hasShownPopup = true;

        TitleScreen self = (TitleScreen) (Object) this;
        Minecraft.getInstance().setScreen(new SyncPopupScreen(self));
    }
}