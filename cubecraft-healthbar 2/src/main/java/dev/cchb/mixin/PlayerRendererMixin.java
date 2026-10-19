package dev.cchb.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cchb.render.HealthBarRenderer;
import dev.cchb.track.BarHolder;
import dev.cchb.track.TargetState;
import dev.cchb.track.TargetTracker;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Three narrow injections, all on player rendering only:
 *  1. extractRenderState (TAIL): decide if this player gets a bar; optionally drop the vanilla
 *     below-name score text (only when it is the indicator we replace).
 *  2. renderNameTag (HEAD/TAIL): if we dropped the score row, keep vanilla's name-row spacing.
 *  3. render (TAIL): draw the bar.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Unique private boolean cchb$pushed;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void cchb$extract(AbstractClientPlayer player, PlayerRenderState state, float partialTick,
                              CallbackInfo ci) {
        BarHolder holder = (BarHolder) state;
        holder.cchb$setBar(null);
        holder.cchb$setShift(false);

        TargetState t = TargetTracker.onExtract(player, state.distanceToCameraSq, state.isInvisible);
        if (t == null) return;

        holder.cchb$setBar(t);
        if (t.hideOriginalNow) {
            state.scoreText = null;          // vanilla below-name row; the bar takes its place
            holder.cchb$setShift(true);
        }
    }

    @Inject(
            method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void cchb$nameHead(PlayerRenderState state, Component name, PoseStack poseStack,
                               MultiBufferSource buffers, int light, CallbackInfo ci) {
        cchb$pushed = false;
        if (((BarHolder) state).cchb$shift()) {
            poseStack.pushPose();
            poseStack.translate(0f, 9f * 1.15f * 0.025f, 0f);   // same shift vanilla applies after the score row
            cchb$pushed = true;
        }
    }

    @Inject(
            method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void cchb$nameTail(PlayerRenderState state, Component name, PoseStack poseStack,
                               MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (cchb$pushed) {
            poseStack.popPose();
            cchb$pushed = false;
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void cchb$render(PlayerRenderState state, PoseStack poseStack, MultiBufferSource buffers,
                             int light, CallbackInfo ci) {
        BarHolder holder = (BarHolder) state;
        TargetState t = holder.cchb$bar();
        if (t != null) {
            HealthBarRenderer.render(state, t, poseStack, buffers, holder.cchb$shift());
        }
    }
}
