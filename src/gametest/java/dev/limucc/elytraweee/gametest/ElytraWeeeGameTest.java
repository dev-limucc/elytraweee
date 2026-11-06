package dev.limucc.elytraweee.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.elytraweee.KeyBindings;
import dev.limucc.elytraweee.compat.ScreenNav;
import dev.limucc.elytraweee.config.ElytraWeeeConfig;
import dev.limucc.elytraweee.integration.ModMenuIntegration;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.function.Predicate;

/**
 * Runtime check of every ElytraWEEE feature in a real survival world, driven with the real keys
 * (jump + keybinds) and verified on BOTH the client and the integrated server.
 * Run with {@code gradlew runClientGameTest "-Ptarget=<mc>"}.
 */
public class ElytraWeeeGameTest implements FabricClientGameTest {

    // inventory indices: 0-8 hotbar, 9-35 main (command "inventory.N" == index N+9)
    private static final int ELYTRA_IDX = 13;        // inventory.4
    private static final int HOTBAR_ELYTRA_IDX = 5;  // hotbar.5

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("EW-GAMETEST FAIL: " + what);
        System.out.println("[EW-GAMETEST] PASS: " + what);
    }

    private static void info(String what) {
        System.out.println("[EW-GAMETEST] INFO: " + what);
    }

    // ---- state readers -----------------------------------------------------------------------
    private static Item clientChest(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem());
    }

    private static Item serverChest(TestSingleplayerContext w) {
        return w.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getItemBySlot(EquipmentSlot.CHEST).getItem());
    }

    private static Item serverSlot(TestSingleplayerContext w, int idx) {
        return w.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().getItem(idx).getItem());
    }

    private static boolean flying(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.isFallFlying());
    }

    private static boolean onGround(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.onGround());
    }

    private static boolean waitUntil(ClientGameTestContext ctx, Predicate<Minecraft> p, int ticks) {
        try {
            ctx.waitFor(p, ticks);
            return true;
        } catch (RuntimeException | AssertionError e) {
            return false;
        }
    }

    /** After a take-off: wait until airborne, then until back on the ground and no longer gliding. */
    private static boolean awaitLanding(ClientGameTestContext ctx) {
        waitUntil(ctx, mc -> !mc.player.onGround(), 15);
        return waitUntil(ctx, mc -> mc.player.onGround() && !mc.player.isFallFlying(), 300);
    }

    private static void cmd(TestSingleplayerContext w, String c) {
        w.getServer().runCommand(c);
    }

    private static void resetCfg(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            ElytraWeeeConfig c = ElytraWeeeConfig.get();
            c.enabled = true;
            c.jumpMode = ElytraWeeeConfig.JumpMode.SINGLE;
            c.swapBackWhenNotHoldingFirework = true;
            c.swapBackOnLanding = false;
            c.graceWindowEnabled = true;
            c.graceWindowTicks = 10;
            c.fastSwapEnabled = true;
            c.fastSwapRevertCooldownTicks = 20;
            c.autoReglideOnFastSwap = true;
        });
    }

    /** InputConstants.Type.KEYSYM was renamed KEYBOARD in 26.3. */
    private static InputConstants.Type keyboard() {
        try {
            return InputConstants.Type.valueOf("KEYSYM");
        } catch (IllegalArgumentException e) {
            return InputConstants.Type.valueOf("KEYBOARD");
        }
    }

    private static void bind(ClientGameTestContext ctx, KeyMapping m, int key) {
        ctx.runOnClient(mc -> {
            m.setKey(keyboard().getOrCreate(key));
            KeyMapping.resetMapping();
        });
    }

    /** Back to the spawn spot on the ground with a clean inventory. hotbarSlot &lt; 0 puts the elytra in inventory.4. */
    private static void stage(ClientGameTestContext ctx, TestSingleplayerContext w, double[] p, String chest, int hotbarSlot) {
        // let any in-flight client container clicks reach the server before we rewrite its inventory
        ctx.waitTicks(6);
        cmd(w, "tp @p " + p[0] + " " + p[1] + " " + p[2]);
        cmd(w, "clear @p");
        if (chest != null) cmd(w, "item replace entity @p armor.chest with " + chest);
        cmd(w, "item replace entity @p " + (hotbarSlot >= 0 ? "hotbar." + hotbarSlot : "inventory.4") + " with minecraft:elytra");
        ctx.waitTicks(4);
        waitUntil(ctx, mc -> mc.player.onGround(), 60);
        ctx.waitTicks(8);
    }

    private static void jump(ClientGameTestContext ctx) {
        ctx.getInput().holdKeyFor(options -> options.keyJump, 2);
    }

    private static void giveRocket(TestSingleplayerContext w) {
        cmd(w, "item replace entity @p weapon.mainhand with minecraft:firework_rocket 16");
    }

    private static void emptyHand(TestSingleplayerContext w) {
        cmd(w, "item replace entity @p weapon.mainhand with minecraft:air");
    }

    @Override
    public void runTest(ClientGameTestContext ctx) throws RuntimeException {
        // 1. Force every mixin to apply now (ElytraWEEE ships none; this also audits Cloth Config / Mod Menu / Fabric API).
        ctx.runOnClient(mc -> MixinEnvironment.getCurrentEnvironment().audit());
        check(true, "MixinEnvironment.audit() passed (all mixins applied)");
        resetCfg(ctx);

        try (TestSingleplayerContext w = ctx.worldBuilder().create()) {
            ctx.waitTicks(60);
            cmd(w, "gamemode survival @p");
            cmd(w, "difficulty peaceful");
            cmd(w, "time set noon");
            cmd(w, "weather clear");
            cmd(w, "effect give @p minecraft:resistance 100000 4 true");
            ctx.waitTicks(20);
            double[] p = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            info("spawn " + p[0] + "," + p[1] + "," + p[2]);
            bind(ctx, KeyBindings.fastSwap, InputConstants.KEY_G);
            bind(ctx, KeyBindings.toggleEnabled, InputConstants.KEY_H);
            bind(ctx, KeyBindings.openConfig, InputConstants.KEY_J);

            // ---- A. jump + firework in hand -> elytra equipped, chestplate swapped to the elytra's old slot, instant glide
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            check(clientChest(ctx) == Items.DIAMOND_CHESTPLATE, "A0 start: diamond chestplate worn");
            giveRocket(w);
            ctx.waitTicks(5);
            jump(ctx);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 25), "A1 jump + firework equips the elytra (client)");
            ctx.waitTicks(3);
            check(serverChest(w) == Items.ELYTRA, "A1 elytra equipped on the SERVER too (server-authoritative swap)");
            check(serverSlot(w, ELYTRA_IDX) == Items.DIAMOND_CHESTPLATE, "A2 chestplate moved into the elytra's old slot (nothing dropped)");
            check(flying(ctx) || waitUntil(ctx, mc -> mc.player.isFallFlying(), 6), "A3 instant glide: isFallFlying() right after equip");
            ctx.takeScreenshot("ew-01-auto-deploy-glide");

            // ---- B. landing: keeps the elytra while still holding a firework; swaps back once the rocket is gone
            check(awaitLanding(ctx), "B0 landed");
            ctx.waitTicks(40);
            check(clientChest(ctx) == Items.ELYTRA, "B1 still holding a firework after landing -> elytra stays on");
            emptyHand(w);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 60), "B2 rocket gone -> chestplate swapped back on landing (client)");
            ctx.waitTicks(4);
            check(serverChest(w) == Items.DIAMOND_CHESTPLATE, "B2 chestplate back on the SERVER");
            check(serverSlot(w, ELYTRA_IDX) == Items.ELYTRA, "B3 elytra returned to its ORIGIN slot");
            ctx.takeScreenshot("ew-02-swap-back-landed");

            // ---- C. empty chest slot: only the elytra goes on; swap-back removes it, never equips a stray chestplate
            stage(ctx, w, p, null, HOTBAR_ELYTRA_IDX);
            cmd(w, "item replace entity @p inventory.20 with minecraft:iron_chestplate");
            giveRocket(w);
            ctx.waitTicks(5);
            jump(ctx);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 25), "C1 empty chest slot: elytra equipped");
            ctx.waitTicks(3);
            check(serverSlot(w, HOTBAR_ELYTRA_IDX) == Items.AIR, "C2 hotbar slot emptied (elytra taken from the HOTBAR)");
            check(awaitLanding(ctx), "C3 landed");
            emptyHand(w);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), 60), "C4 swap-back removes the elytra and does NOT equip the inventory chestplate");
            ctx.waitTicks(4);
            check(serverSlot(w, HOTBAR_ELYTRA_IDX) == Items.ELYTRA, "C5 elytra returned to its ORIGIN hotbar slot");
            check(serverSlot(w, 29) == Items.IRON_CHESTPLATE, "C6 stray chestplate untouched");

            // ---- D. swapBackOnLanding: swap back the instant you land even while holding a firework
            resetCfg(ctx);
            ctx.runOnClient(mc -> ElytraWeeeConfig.get().swapBackOnLanding = true);
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            giveRocket(w);
            ctx.waitTicks(5);
            jump(ctx);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 25), "D1 equipped (swapBackOnLanding=true)");
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 200), "D2 swapped back on landing while STILL holding the firework");
            resetCfg(ctx);

            // ---- E. grace window: jump first, grab the firework afterwards
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            emptyHand(w);
            ctx.waitTicks(4);
            jump(ctx);
            ctx.waitTicks(1);
            giveRocket(w);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 25), "E1 grace window: firework grabbed AFTER the jump still deploys the elytra");
            check(awaitLanding(ctx), "E2 landed");
            emptyHand(w);
            waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 60);

            // E-neg: grace window off -> same sequence must NOT deploy
            ctx.runOnClient(mc -> ElytraWeeeConfig.get().graceWindowEnabled = false);
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            emptyHand(w);
            ctx.waitTicks(4);
            jump(ctx);
            ctx.waitTicks(1);
            giveRocket(w);
            ctx.waitTicks(25);
            check(clientChest(ctx) == Items.DIAMOND_CHESTPLATE, "E3 grace window disabled -> late firework does not deploy");
            resetCfg(ctx);

            // ---- F. double-jump mode
            ctx.runOnClient(mc -> ElytraWeeeConfig.get().jumpMode = ElytraWeeeConfig.JumpMode.DOUBLE);
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            giveRocket(w);
            ctx.waitTicks(5);
            jump(ctx);
            ctx.waitTicks(12);
            check(clientChest(ctx) == Items.DIAMOND_CHESTPLATE, "F1 double-jump mode: ONE jump does not deploy");
            check(awaitLanding(ctx), "F1 landed");
            ctx.waitTicks(10);
            jump(ctx);
            ctx.waitTicks(3);
            jump(ctx);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 25), "F2 double-jump mode: two quick jumps deploy");
            check(awaitLanding(ctx), "F2 landed");
            emptyHand(w);
            waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 60);
            resetCfg(ctx);

            // ---- G. master switch keybind
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            giveRocket(w);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(KeyBindings.toggleEnabled);
            ctx.waitTicks(3);
            check(!ctx.computeOnClient(mc -> ElytraWeeeConfig.get().enabled), "G1 toggle keybind disables the mod");
            jump(ctx);
            ctx.waitTicks(15);
            check(clientChest(ctx) == Items.DIAMOND_CHESTPLATE, "G2 disabled -> jump + firework does nothing (chest=" + clientChest(ctx) + ")");
            check(awaitLanding(ctx), "G2 landed");
            ctx.getInput().pressKey(KeyBindings.toggleEnabled);
            ctx.waitTicks(3);
            check(ctx.computeOnClient(mc -> ElytraWeeeConfig.get().enabled), "G3 toggle keybind re-enables");
            ctx.waitTicks(10);
            jump(ctx);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 25), "G4 re-enabled -> deploys again");
            check(awaitLanding(ctx), "G4 landed");
            emptyHand(w);
            waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 60);

            // ---- H. fast-swap keybind on the ground: elytra <-> chestplate, and the mod does NOT auto-revert it
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            ctx.getInput().pressKey(KeyBindings.fastSwap);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 15), "H1 fast-swap key: chestplate -> elytra");
            ctx.waitTicks(4);
            check(serverChest(w) == Items.ELYTRA && serverSlot(w, ELYTRA_IDX) == Items.DIAMOND_CHESTPLATE, "H1 server agrees, chestplate in the elytra's old slot");
            ctx.waitTicks(60);
            check(clientChest(ctx) == Items.ELYTRA, "H2 manually/fast-swapped elytra is NOT auto-reverted on the ground");
            ctx.takeScreenshot("ew-03-fast-swap-elytra-on");
            ctx.getInput().pressKey(KeyBindings.fastSwap);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 15), "H3 fast-swap key again: elytra -> chestplate");
            ctx.waitTicks(4);
            check(serverSlot(w, ELYTRA_IDX) == Items.ELYTRA, "H3 elytra back in its slot on the server");
            ctx.runOnClient(mc -> ElytraWeeeConfig.get().fastSwapEnabled = false);
            ctx.getInput().pressKey(KeyBindings.fastSwap);
            ctx.waitTicks(10);
            check(clientChest(ctx) == Items.DIAMOND_CHESTPLATE, "H4 fast-swap disabled in config -> key does nothing");
            resetCfg(ctx);

            // ---- I. fast-swap mid-air + auto re-glide
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            cmd(w, "tp @p " + p[0] + " " + (p[1] + 60) + " " + p[2]);
            ctx.waitTicks(8);
            check(!onGround(ctx) && !flying(ctx), "I0 airborne and falling (not gliding)");
            ctx.getInput().pressKey(KeyBindings.fastSwap);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 15), "I1 mid-air fast-swap equips the elytra");
            check(waitUntil(ctx, mc -> mc.player.isFallFlying(), 20), "I2 auto re-glide: gliding again without tapping jump");
            ctx.waitTicks(10);
            ctx.takeScreenshot("ew-04-mid-air-reglide");
            ctx.getInput().pressKey(KeyBindings.fastSwap);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 15), "I3 mid-air fast-swap back to the chestplate");
            check(waitUntil(ctx, mc -> !mc.player.isFallFlying(), 20), "I3 gliding stops once the elytra is off");
            ctx.runOnClient(mc -> ElytraWeeeConfig.get().autoReglideOnFastSwap = false);
            ctx.waitTicks(25);   // clear the fast-swap cooldown
            ctx.getInput().pressKey(KeyBindings.fastSwap);
            check(waitUntil(ctx, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), 15), "I4 (re-glide off) mid-air swap still equips");
            ctx.waitTicks(10);
            check(!flying(ctx) || onGround(ctx), "I4 auto re-glide OFF -> does not start gliding by itself");
            cmd(w, "tp @p " + p[0] + " " + p[1] + " " + p[2]);
            resetCfg(ctx);
            ctx.waitTicks(10);

            // ---- J. settings screen (Cloth Config) via keybind and via Mod Menu
            stage(ctx, w, p, "minecraft:diamond_chestplate", -1);
            ctx.getInput().resizeWindow(1280, 720);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(KeyBindings.openConfig);
            check(waitUntil(ctx, mc -> ScreenNav.current(mc) != null, 20), "J1 open-config keybind opens a screen");
            String cls = ctx.computeOnClient(mc -> ScreenNav.current(mc).getClass().getName());
            check(cls.contains("clothconfig2"), "J1 it is the Cloth Config screen: " + cls);
            ctx.waitTicks(5);
            ctx.takeScreenshot("ew-05-config-general");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> ScreenNav.current(mc) == null), "J2 Escape closes the config screen");
            ctx.setScreen(() -> new ModMenuIntegration().getModConfigScreenFactory().create(null));
            ctx.waitTicks(5);
            String cls2 = ctx.computeOnClient(mc -> ScreenNav.current(mc).getClass().getName());
            check(cls2.contains("clothconfig2"), "J3 Mod Menu config factory builds the Cloth screen: " + cls2);
            ctx.takeScreenshot("ew-06-config-from-modmenu");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            System.out.println("[EW-GAMETEST] PASS: ALL ElytraWEEE checks complete");
        }
    }
}
