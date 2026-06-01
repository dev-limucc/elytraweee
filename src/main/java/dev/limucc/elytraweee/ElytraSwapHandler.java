package dev.limucc.elytraweee;

import dev.limucc.elytraweee.config.ElytraWeeeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Core ElytraWEEE behaviour, ticked every client tick.
 *
 * <p>All inventory mutations go through {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#handleContainerInput}
 * on the always-open player inventory menu (container id 0), so the swaps are server-authoritative and never drop items.
 *
 * <p>Player inventory menu slot layout: 5 = helmet, 6 = chest, 7 = legs, 8 = boots,
 * 9-35 = main inventory, 36-44 = hotbar, 45 = offhand.
 */
public class ElytraSwapHandler {

    private static final int CHEST_SLOT = 6;
    private static final int FIRST_STORAGE_SLOT = 9; // start of inventory/hotbar/offhand in the menu
    private static final int DOUBLE_JUMP_WINDOW_TICKS = 8;
    private static final int LAND_REVERT_DELAY_TICKS = 5;
    /** How long after an equip we keep trying to start gliding (until airborne conditions are met). */
    private static final int GLIDE_ATTEMPT_TICKS = 10;

    private enum State {
        IDLE,
        EQUIPPED
    }

    private State state = State.IDLE;
    private boolean chestWasOccupied = false;
    private int groundedTicks = 0;
    /** Menu slot the elytra was taken from on equip, so it can be returned there. -1 if unknown. */
    private int elytraOriginSlot = -1;
    /**
     * True only when the elytra currently worn was put on by the mod's auto-deploy (jump + firework)
     * flow. The automatic landing swap-back runs ONLY in this case. An elytra you equipped yourself —
     * manually, or with the fast-swap key — is left exactly as you set it. This mod enhances elytra
     * usability; it does not take 100% control of your armour.
     */
    private boolean autoDeployedForFlight = false;

    private boolean prevJumpDown = false;
    private int firstJumpTick = 0;
    private long tickCounter = 0;

    /** Tick of a jump that is "armed" and waiting for a firework (grace window). 0 = none. */
    private int pendingJumpTick = 0;
    /** Until this tick, the automatic landing swap-back is suppressed (so fast-swap is not undone). */
    private long suppressAutoRevertUntilTick = 0;
    /** While &gt; tickCounter, keep trying to start gliding so the player flies immediately after equip. */
    private long glideUntilTick = 0;

    public void tick(Minecraft client) {
        tickCounter++;

        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            reset();
            glideUntilTick = 0;
            prevJumpDown = false;
            return;
        }

        ElytraWeeeConfig cfg = ElytraWeeeConfig.get();
        boolean jumpDown = client.options.keyJump.isDown();

        if (!cfg.enabled) {
            reset();
            glideUntilTick = 0;
            prevJumpDown = jumpDown;
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        // Only act when the player's own inventory menu is active (no chest/GUI open) and the cursor is empty.
        boolean canClick = menu == player.inventoryMenu
                && client.screen == null
                && menu.getCarried().isEmpty()
                && menu.slots.size() > CHEST_SLOT;

        boolean wearingElytra = player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        boolean holdingFirework = isFirework(player.getMainHandItem()) || isFirework(player.getOffhandItem());

        // Track how long we have been continuously on the ground while wearing an elytra.
        if (wearingElytra && player.onGround()) {
            groundedTicks++;
        } else {
            groundedTicks = 0;
        }

        // --- Jump detection (rising edge of the jump key) ---
        boolean jumpEdge = jumpDown && !prevJumpDown && client.screen == null;
        prevJumpDown = jumpDown;

        boolean triggerEquip = false;
        // A qualifying jump (per jump mode) either equips right away (firework already held) or arms
        // a grace window so that grabbing a firework a moment later still deploys the elytra.
        if (jumpEdge && !wearingElytra && state == State.IDLE && jumpModeSatisfied(cfg)) {
            if (holdingFirework) {
                triggerEquip = true;
                pendingJumpTick = 0;
            } else if (cfg.graceWindowEnabled) {
                pendingJumpTick = (int) tickCounter;
            }
        }

        // --- Grace window: deploy if a firework is grabbed shortly after a jump, while airborne ---
        if (pendingJumpTick > 0) {
            if (tickCounter - pendingJumpTick > cfg.graceWindowTicks) {
                pendingJumpTick = 0; // expired
            } else if (wearingElytra || state != State.IDLE) {
                pendingJumpTick = 0; // no longer applicable
            } else if (player.onGround() && tickCounter > pendingJumpTick + 1) {
                // Back on the ground without ever leaving (the +1 skips the take-off tick where
                // onGround() can still read true): the jump did not lead to flight, so drop it.
                pendingJumpTick = 0;
            } else if (holdingFirework) {
                triggerEquip = true;
                pendingJumpTick = 0;
            }
        }

        if (triggerEquip && canClick) {
            int elytraSlot = findElytraSlot(menu);
            if (elytraSlot >= 0) {
                boolean chestOccupied = menu.getSlot(CHEST_SLOT).hasItem();
                equipElytra(client, player, menu, elytraSlot, chestOccupied);
                state = State.EQUIPPED;
                chestWasOccupied = chestOccupied;
                groundedTicks = 0;
                pendingJumpTick = 0;
                // This elytra was deployed by the mod for flight, so we own the landing swap-back.
                autoDeployedForFlight = true;
                // We just put the elytra on; treat it as worn for the rest of this tick so the
                // swap-back gate below does not see the stale (pre-equip) value and wrongly reset.
                wearingElytra = true;
                // Start gliding right away so a jump-equip (incl. double jump) can fly immediately,
                // instead of forcing the player to tap jump again to enter flight mode.
                glideUntilTick = tickCounter + GLIDE_ATTEMPT_TICKS;
            }
        }

        // --- Auto-start gliding after a mid-air equip / re-glide ---
        // tryToStartFallFlying() only succeeds once airborne, so we retry for a few ticks.
        if (glideUntilTick > 0) {
            boolean stillElytra = player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
            if (tickCounter > glideUntilTick || player.onGround() || player.isFallFlying() || !stillElytra) {
                glideUntilTick = 0;
            } else {
                tryStartGliding(player);
                if (player.isFallFlying()) {
                    glideUntilTick = 0;
                }
            }
        }

        // --- Swap back when landed ---
        // ONLY for an elytra this mod auto-deployed for flight. An elytra you put on yourself —
        // manually or with the fast-swap key — is left exactly as you set it; the mod never reverts
        // your own armour choices. It enhances elytra usability, it does not control your gear.
        if (!wearingElytra || !autoDeployedForFlight) {
            // Not our deployment: clear any stale auto-deploy state so we never act on it later.
            if (state == State.EQUIPPED && !wearingElytra) {
                reset();
            }
        } else if (!triggerEquip && canClick && tickCounter >= suppressAutoRevertUntilTick) {
            boolean landed = player.onGround() && !player.isFallFlying()
                    && groundedTicks >= LAND_REVERT_DELAY_TICKS;
            boolean revert = false;
            if (landed) {
                // Optional: swap back the instant you land, even while still holding a firework.
                if (cfg.swapBackOnLanding) {
                    revert = true;
                }
                // Default: once landed and no longer holding a firework, swap back. Keep holding a
                // firework after landing to stay in the elytra and take off again.
                if (cfg.swapBackWhenNotHoldingFirework && !holdingFirework) {
                    revert = true;
                }
            }
            if (revert) {
                boolean done;
                if (!chestWasOccupied) {
                    // We took off with an empty chest slot (no chestplate was worn): just remove the
                    // elytra — never auto-equip a chestplate the player did not have on, even if one
                    // is in their inventory. If the inventory is full this leaves the elytra on.
                    done = removeElytraToInventory(client, player, menu);
                } else {
                    // Restore the chestplate we swapped out on take-off.
                    done = swapBackToChestplate(client, player, menu);
                    if (!done) {
                        done = removeElytraToInventory(client, player, menu);
                    }
                }
                if (done) {
                    reset();
                } else {
                    // Could not complete (e.g. inventory full): keep the elytra on, but throttle
                    // retries to avoid spamming clicks every tick — try again in ~half a second.
                    suppressAutoRevertUntilTick = tickCounter + 10;
                }
            }
        }
    }

    /**
     * Whether the current jump satisfies the configured jump mode. SINGLE: always. DOUBLE: only on
     * the second jump within {@link #DOUBLE_JUMP_WINDOW_TICKS}, tracking the first jump's tick.
     * Only call on a real jump edge.
     */
    private boolean jumpModeSatisfied(ElytraWeeeConfig cfg) {
        if (cfg.jumpMode == ElytraWeeeConfig.JumpMode.SINGLE) {
            return true;
        }
        if (firstJumpTick > 0 && (tickCounter - firstJumpTick) <= DOUBLE_JUMP_WINDOW_TICKS) {
            firstJumpTick = 0;
            return true;
        }
        firstJumpTick = (int) tickCounter;
        return false;
    }

    /**
     * Instantly toggle between the elytra and a chestplate, including mid-air (unlike the automatic
     * swap-back, which only runs on the ground). Bound to the Fast Swap keybind — built for mace PvP
     * where you need to switch in the air. Reuses the same no-drop inventory routines.
     */
    public void fastSwap(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            return;
        }
        ElytraWeeeConfig cfg = ElytraWeeeConfig.get();
        // Independent of the master switch so PvP players can keep the manual swap with auto-deploy off.
        if (!cfg.fastSwapEnabled) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        boolean canClick = menu == player.inventoryMenu
                && client.screen == null
                && menu.getCarried().isEmpty()
                && menu.slots.size() > CHEST_SLOT;
        if (!canClick) {
            return;
        }

        boolean wearingElytra = player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        if (wearingElytra) {
            // Swap to a chestplate if one is available; otherwise just take the elytra off, returning
            // it to the slot it came from (or any free slot). If the inventory is full, leave it on.
            boolean done = swapBackToChestplate(client, player, menu);
            if (!done) {
                done = removeElytraToInventory(client, player, menu);
            }
            if (done) {
                reset();
                suppressAutoRevertUntilTick = tickCounter + cfg.fastSwapRevertCooldownTicks;
            }
            return;
        }

        int elytraSlot = findElytraSlot(menu);
        if (elytraSlot < 0) {
            return;
        }
        boolean chestOccupied = menu.getSlot(CHEST_SLOT).hasItem();
        equipElytra(client, player, menu, elytraSlot, chestOccupied);
        state = State.EQUIPPED;
        chestWasOccupied = chestOccupied;
        groundedTicks = 0;
        pendingJumpTick = 0;
        // You chose to put this elytra on via the key — the mod will NOT auto-revert it on landing.
        autoDeployedForFlight = false;
        suppressAutoRevertUntilTick = tickCounter + cfg.fastSwapRevertCooldownTicks;
        // Auto re-glide: if you swapped back to the elytra while airborne, resume flight automatically
        // so you don't have to tap jump twice. Toggle, on by default.
        if (cfg.autoReglideOnFastSwap && !player.onGround()) {
            glideUntilTick = tickCounter + GLIDE_ATTEMPT_TICKS;
        }
    }

    /** Begin elytra flight client-side and tell the server, exactly as vanilla LocalPlayer does. */
    private void tryStartGliding(LocalPlayer player) {
        if (player.tryToStartFallFlying()) {
            player.connection.send(new ServerboundPlayerCommandPacket(
                    player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    // ---- Equip / revert routines (cursor-balanced; never drop items) ----

    private void equipElytra(Minecraft client, Player player, AbstractContainerMenu menu, int elytraSlot, boolean chestOccupied) {
        int id = menu.containerId;
        elytraOriginSlot = elytraSlot; // remember where the elytra came from so we can return it
        if (chestOccupied) {
            // Swap: elytra -> chest, chestplate -> elytra's old slot.
            pickup(client, player, id, elytraSlot);
            pickup(client, player, id, CHEST_SLOT);
            pickup(client, player, id, elytraSlot);
        } else {
            // Chest empty: just move the elytra in.
            pickup(client, player, id, elytraSlot);
            pickup(client, player, id, CHEST_SLOT);
        }
    }

    /**
     * Put a chestplate back on, moving the worn elytra into the inventory. Works regardless of
     * whether the mod equipped the elytra this session. Returns false when there is no chestplate
     * available to swap to (the caller then decides whether to just remove the elytra).
     */
    private boolean swapBackToChestplate(Minecraft client, Player player, AbstractContainerMenu menu) {
        int id = menu.containerId;
        int chestplateSlot = findChestplateSlot(menu);
        if (chestplateSlot >= 0) {
            // Swap: chestplate -> chest, elytra -> the chestplate's old slot.
            pickup(client, player, id, CHEST_SLOT);
            pickup(client, player, id, chestplateSlot);
            pickup(client, player, id, CHEST_SLOT);
            return true;
        }
        return false; // No chestplate available.
    }

    /**
     * Take the worn elytra off and return it to the inventory — preferring its original slot (even a
     * hotbar slot) so the player's layout is preserved. If that slot is taken, shift it to the first
     * free slot. If the inventory is completely full, the elytra stays on (nothing is ever dropped).
     *
     * @return true if the elytra was actually moved off, false if it had to stay on.
     */
    private boolean removeElytraToInventory(Minecraft client, Player player, AbstractContainerMenu menu) {
        int id = menu.containerId;
        // Prefer the exact origin slot if we know it and it is currently empty.
        if (elytraOriginSlot >= FIRST_STORAGE_SLOT && elytraOriginSlot < menu.slots.size()
                && menu.getSlot(elytraOriginSlot).getItem().isEmpty()) {
            pickup(client, player, id, CHEST_SLOT);          // elytra -> cursor
            pickup(client, player, id, elytraOriginSlot);    // elytra -> its original slot
            return true;
        }
        // Otherwise shift it into the first free inventory slot. QUICK_MOVE moves nothing when the
        // inventory is full, so the elytra simply stays on — exactly the desired behaviour.
        quickMove(client, player, id, CHEST_SLOT);
        return menu.getSlot(CHEST_SLOT).getItem().getItem() != Items.ELYTRA;
    }

    // ---- Helpers ----

    private int findElytraSlot(AbstractContainerMenu menu) {
        for (int i = FIRST_STORAGE_SLOT; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().getItem() == Items.ELYTRA) {
                return i;
            }
        }
        return -1;
    }

    private int findChestplateSlot(AbstractContainerMenu menu) {
        for (int i = FIRST_STORAGE_SLOT; i < menu.slots.size(); i++) {
            if (isChestplate(menu.getSlot(i).getItem())) {
                return i;
            }
        }
        return -1;
    }

    /** True for any item worn in the chest equipment slot, excluding the elytra itself. */
    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == Items.ELYTRA) {
            return false;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static boolean isFirework(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.FIREWORK_ROCKET;
    }

    private void pickup(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
    }

    private void quickMove(Minecraft client, Player player, int containerId, int slot) {
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
    }

    private void reset() {
        state = State.IDLE;
        chestWasOccupied = false;
        groundedTicks = 0;
        firstJumpTick = 0;
        pendingJumpTick = 0;
        elytraOriginSlot = -1;
        autoDeployedForFlight = false;
        // Note: suppressAutoRevertUntilTick and glideUntilTick are intentionally NOT reset here —
        // they must outlive a swap (glide may begin the tick after the elytra goes on).
    }
}
