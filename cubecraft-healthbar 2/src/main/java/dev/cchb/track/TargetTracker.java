package dev.cchb.track;

import dev.cchb.CcHealthBarMod;
import dev.cchb.config.ModConfig;
import dev.cchb.server.ServerGate;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads what the client legitimately knows (server-sent scoreboard rows / entity data) and decides,
 * per opponent, whether a bar should exist and what its target ratio is. Nothing here is sent to the
 * server and nothing here changes any game value.
 */
public final class TargetTracker {
    private static final Int2ObjectOpenHashMap<TargetState> STATES = new Int2ObjectOpenHashMap<>();
    private static int tickCounter;

    private TargetTracker() { }

    public static void clear() {
        STATES.clear();
    }

    private static TargetState get(int id) {
        TargetState s = STATES.get(id);
        if (s == null) {
            s = new TargetState(id);
            STATES.put(id, s);
        }
        return s;
    }

    // ---- combat signals (approximation only) -------------------------------------------

    public static void markAttacked(int targetId) {
        get(targetId).lastIHitMs = System.currentTimeMillis();
    }

    public static void markAttackedBy(int attackerId) {
        get(attackerId).lastHitMeMs = System.currentTimeMillis();
    }

    // ---- per-frame decision (called from PlayerRenderer.extractRenderState) -------------

    /** @return the state to draw a bar for, or null when no bar should be shown for this player. */
    public static TargetState onExtract(AbstractClientPlayer p, double distSq, boolean invisible) {
        ModConfig c = ModConfig.get();
        if (!c.enabled || !ServerGate.active()) return null;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || p == mc.player) return null;
        if (invisible || p.isSpectator() || !p.isAlive()) return null;
        double maxD = c.maxDistance;
        if (distSq > maxD * maxD) return null;

        long now = System.currentTimeMillis();
        TargetState t = get(p.getId());
        t.lastSeenMs = now;

        // What did the server send us under the player's name?
        Scoreboard board = level.getScoreboard();
        Objective obj = board.getDisplayObjective(DisplaySlot.BELOW_NAME);
        ReadOnlyScoreInfo info = obj != null ? board.getPlayerScoreInfo(p, obj) : null;

        boolean forced = c.sourceMode == ModConfig.HealthSource.SCOREBOARD
                || c.visibilityMode == ModConfig.Visibility.SCOREBOARD;
        boolean looksLikeHealth = obj != null
                && ("health".equals(obj.getCriteria().getName())
                || (!c.scoreboardObjectiveName.isEmpty() && c.scoreboardObjectiveName.equals(obj.getName())));
        boolean objUsable = obj != null && (looksLikeHealth || forced);
        boolean scoreAvail = objUsable && info != null;

        // Visibility: does the server consider this opponent eligible for a health indicator?
        boolean eligible;
        switch (c.visibilityMode) {
            case ALWAYS -> eligible = true;
            case SCOREBOARD -> eligible = scoreAvail;
            case COMBAT_TIMER -> eligible = combatRecent(t, now, c);
            default -> eligible = objUsable ? scoreAvail : combatRecent(t, now, c);   // AUTO
        }
        if (c.debug && eligible != t.eligible) {
            CcHealthBarMod.LOGGER.info("[cchb] {} eligible={} (objUsable={}, scoreAvail={}, mode={})",
                    p.getScoreboardName(), eligible, objUsable, scoreAvail, c.visibilityMode);
        }
        t.eligible = eligible;
        if (!eligible) return null;

        // Health value: scoreboard row when that's the indicator, otherwise entity data.
        boolean useScore = switch (c.sourceMode) {
            case SCOREBOARD -> true;
            case ENTITY -> false;
            default -> scoreAvail;
        };
        float health;
        float max;
        float absorption = 0f;
        if (useScore) {
            if (!scoreAvail || info == null) return null;
            health = info.value();
            max = c.scoreboardMaxHealth;
        } else {
            health = p.getHealth();
            max = p.getMaxHealth();
            absorption = p.getAbsorptionAmount();
        }
        if (Float.isNaN(health) || !(max > 0f)) return null;

        float ratio = health / max;
        t.targetRatio = ratio < 0f ? 0f : Math.min(ratio, 1f);
        float ar = absorption / max;
        t.absRatio = ar <= 0f || Float.isNaN(ar) ? 0f : Math.min(ar, 1f);

        int shown = (int) Math.ceil(health);
        if (shown != t.shownValue) {
            t.shownValue = shown;
            t.numericText = Integer.toString(shown);
        }

        // Only hide the vanilla below-name row when it is the thing we're replacing.
        t.hideOriginalNow = c.hideOriginal && scoreAvail;
        return t;
    }

    private static boolean combatRecent(TargetState t, long now, ModConfig c) {
        long w = c.combatWindowMs;
        return (t.lastIHitMs != 0 && now - t.lastIHitMs <= w)
                || (t.lastHitMeMs != 0 && now - t.lastHitMeMs <= w);
    }

    // ---- housekeeping ---------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (++tickCounter < 40) return;
        tickCounter = 0;
        ClientLevel level = mc.level;
        if (level == null) {
            STATES.clear();
            return;
        }
        long now = System.currentTimeMillis();
        STATES.values().removeIf(s -> {
            long touched = Math.max(s.lastSeenMs, Math.max(s.lastIHitMs, s.lastHitMeMs));
            return level.getEntity(s.entityId) == null || now - touched > 60_000L;
        });
    }
}
