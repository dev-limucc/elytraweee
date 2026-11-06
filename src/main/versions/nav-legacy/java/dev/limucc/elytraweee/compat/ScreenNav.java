package dev.limucc.elytraweee.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** MC 1.21.x-26.1: the current screen lives directly on {@link Minecraft}. */
public final class ScreenNav {

    private ScreenNav() {}

    public static Screen current(Minecraft mc) {
        return mc.screen;
    }

    public static void open(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }
}
