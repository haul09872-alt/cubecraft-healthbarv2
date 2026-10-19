package dev.cchb.mixin;

import dev.cchb.track.TargetTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only: notes who damaged the local player. Only feeds the COMBAT_TIMER approximation
 * (the server may also show the indicator when you are hit, not just when you hit).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void cchb$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || packet.entityId() != mc.player.getId()) return;
        Entity source = packet.getSource(level).getEntity();
        if (source instanceof AbstractClientPlayer attacker) {
            TargetTracker.markAttackedBy(attacker.getId());
        }
    }
}
