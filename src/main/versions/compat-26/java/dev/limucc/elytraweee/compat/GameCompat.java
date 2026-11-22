package dev.limucc.elytraweee.compat;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

/** MC 26.x: ContainerInput clicks, action-bar message API, KeyMappingHelper. */
public final class GameCompat {

    private GameCompat() {}

    public static void pickup(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
    }

    public static void quickMove(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
    }

    public static void overlayMessage(LocalPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }

    public static KeyMapping registerKey(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }
}
