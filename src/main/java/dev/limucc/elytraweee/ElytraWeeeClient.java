package dev.limucc.elytraweee;

import dev.limucc.elytraweee.config.ElytraWeeeConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint. Loads the config and drives the swap logic each client tick.
 */
public class ElytraWeeeClient implements ClientModInitializer {

    public static final String MOD_ID = "elytraweee";

    private final ElytraSwapHandler handler = new ElytraSwapHandler();

    @Override
    public void onInitializeClient() {
        ElytraWeeeConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
    }

    private void onEndClientTick(Minecraft client) {
        handler.tick(client);
    }
}
