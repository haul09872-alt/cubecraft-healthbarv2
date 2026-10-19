package dev.cchb.track;

import dev.cchb.config.ModConfig;

/** Per-opponent state: latest known health (target) and what the animation currently shows. */
public final class TargetState {
    public final int entityId;

    // combat signals (only used by the COMBAT_TIMER approximation); 0 = never
    long lastIHitMs;
    long lastHitMeMs;
    long lastSeenMs;

    // latest known data, written during render-state extraction
    boolean eligible;
    public boolean hideOriginalNow;
    public float targetRatio;
    public float absRatio;
    public String numericText = "";
    int shownValue = Integer.MIN_VALUE;

    // animation (visual only)
    public float displayed;
    private boolean initialized;
    private long lastStepNanos;

    private static final long RESET_GAP_NANOS = 1_500_000_000L;

    TargetState(int entityId) {
        this.entityId = entityId;
    }

    /**
     * Move the displayed ratio toward the newest target using a time-based exponential factor:
     * factor = 1 - exp(-dt / tau). Frame-rate independent; a new hit never restarts anything,
     * it just changes the target the bar is already gliding toward. Works for damage and healing.
     */
    public void step(long nowNanos, ModConfig c) {
        long gap = nowNanos - lastStepNanos;
        if (!initialized || gap > RESET_GAP_NANOS || gap < 0) {
            // first appearance (or reappearance after being hidden): start at the known value
            displayed = targetRatio;
            initialized = true;
        } else if (!c.smoothAnimation) {
            displayed = targetRatio;
        } else if (gap > 0) {
            double dt = Math.min(gap / 1.0e9, 0.25);
            double tau = Math.max(c.smoothingTimeMs, 1f) / 1000.0;
            float f = (float) (1.0 - Math.exp(-dt / tau));
            displayed += (targetRatio - displayed) * f;
            if (Math.abs(targetRatio - displayed) < 0.0015f) displayed = targetRatio;
        }
        if (displayed < 0f) displayed = 0f;
        else if (displayed > 1f) displayed = 1f;
        lastStepNanos = nowNanos;
    }
}
