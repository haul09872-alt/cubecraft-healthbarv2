package dev.cchb.track;

/** Duck interface mixed into EntityRenderState so the renderer can see the target's bar state. */
public interface BarHolder {
    TargetState cchb$bar();

    void cchb$setBar(TargetState state);

    boolean cchb$shift();

    void cchb$setShift(boolean shift);
}
