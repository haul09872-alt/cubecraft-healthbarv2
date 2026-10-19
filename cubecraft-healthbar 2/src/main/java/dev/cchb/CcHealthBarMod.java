package dev.cchb;

import dev.cchb.command.HealthBarCommand;
import dev.cchb.config.ModConfig;
import dev.cchb.server.ServerGate;
import dev.cchb.track.TargetTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CcHealthBarMod implements ClientModInitializer {
    public static final String ID = "cubecraft-healthbar";
    public static final Logger LOGGER = LoggerFactory.getLogger("CubeCraftHealthBar");

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        // World/server changes: drop all per-target state.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TargetTracker.clear();
            ServerGate.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TargetTracker.clear();
            ServerGate.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(TargetTracker::tick);

        // Combat signal (only used by the COMBAT_TIMER approximation): "I hit this player".
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()
                    && player == Minecraft.getInstance().player
                    && entity instanceof AbstractClientPlayer target) {
                TargetTracker.markAttacked(target.getId());
            }
            return InteractionResult.PASS;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                HealthBarCommand.register(dispatcher));

        LOGGER.info("CubeCraft Health Bar loaded (inactive until connected to CubeCraft).");
    }
}
