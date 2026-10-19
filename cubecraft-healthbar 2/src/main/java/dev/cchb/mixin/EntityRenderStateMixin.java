package dev.cchb.mixin;

import dev.cchb.track.BarHolder;
import dev.cchb.track.TargetState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries our per-frame bar decision from render-state extraction to render. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements BarHolder {
    @Unique private TargetState cchb$bar;
    @Unique private boolean cchb$shift;

    @Override public TargetState cchb$bar() { return cchb$bar; }
    @Override public void cchb$setBar(TargetState state) { this.cchb$bar = state; }
    @Override public boolean cchb$shift() { return cchb$shift; }
    @Override public void cchb$setShift(boolean shift) { this.cchb$shift = shift; }
}
