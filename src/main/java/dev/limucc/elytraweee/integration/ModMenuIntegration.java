package dev.limucc.elytraweee.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.limucc.elytraweee.config.ConfigScreenBuilder;

/**
 * Hooks the Cloth Config screen into Mod Menu's config button.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreenBuilder::build;
    }
}
