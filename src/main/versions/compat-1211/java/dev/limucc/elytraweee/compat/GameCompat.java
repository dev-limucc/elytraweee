package dev.limucc.elytraweee.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;

/** MC 1.21.11: ClickType clicks, displayClientMessage(.., true) for the action bar, KeyBindingHelper. */
public final class GameCompat {

    private GameCompat() {}

    public static void pickup(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, player);
    }

    public static void quickMove(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.QUICK_MOVE, player);
    }

    public static void overlayMessage(LocalPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    public static KeyMapping registerKey(KeyMapping mapping) {
        return KeyBindingHelper.registerKeyBinding(mapping);
    }
}
