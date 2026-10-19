package dev.cchb.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cchb.config.ModConfig;
import dev.cchb.track.TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the bar in the same camera-facing, 0.025-scale space as vanilla nametag text.
 *
 * All version-sensitive calls (render type, vertex builder, Font call) live in this one class so a
 * 1.21.7 mapping mismatch can be fixed in one place.
 */
public final class HealthBarRenderer {
    /** Vanilla spacing between the name row and the score row: 9 px * 1.15. */
    private static final float ROW_PX = 9f * 1.15f;
    /** Gap between the name text's bottom edge and the next row's top edge. */
    private static final float NAME_GAP_PX = ROW_PX - 8f;
    private static final float PX = 0.025f;
    /** Small depth step: higher z = nearer the camera in this space. */
    private static final float Z_OVERLAY = 0.1f;
    private static final int FULL_BRIGHT = 0xF000F0;

    private HealthBarRenderer() { }

    public static void render(PlayerRenderState state, TargetState t, PoseStack ps,
                              MultiBufferSource buffers, boolean nameShiftedUp) {
        ModConfig c = ModConfig.get();
        t.step(System.nanoTime(), c);
        float ratio = t.displayed;

        // Anchor: same point vanilla uses for the nametag (attachment point + 0.5 blocks).
        Vec3 att = state.nameTagAttachment;
        double ax = att != null ? att.x : 0.0;
        double ay = att != null ? att.y : state.boundingBoxHeight;
        double az = att != null ? att.z : 0.0;

        // Vertical placement (px, y grows downward). The bar takes the row vanilla uses for the
        // below-name score. If we did not shift the name up, that row is one row lower.
        float slotTop = nameShiftedUp ? 0f : ROW_PX;
        float top = slotTop + c.referenceTopPx - c.closerFraction * c.height + c.verticalOffset;
        float minTop = slotTop - (NAME_GAP_PX - c.minClearancePx);
        if (top < minTop) top = minTop;

        ps.pushPose();
        ps.translate(ax, ay + 0.5, az);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float s = PX * c.scale;
        ps.scale(s, -s, s);
        Matrix4f m = ps.last().pose();

        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());

        float half = c.width * 0.5f;
        float b = c.borderPx;
        float x0 = -half, y0 = top, x1 = half, y1 = top + c.height;
        float ix0 = x0 + b, iy0 = y0 + b, ix1 = x1 - b, iy1 = y1 - b;

        // Border: four non-overlapping rects (no z-fighting, no tint bleeding into the track).
        int border = 0xE6000000 | (c.cBorder & 0xFFFFFF);
        if (b > 0f) {
            quad(vc, m, x0, y0, x1, iy0, 0f, border);
            quad(vc, m, x0, iy1, x1, y1, 0f, border);
            quad(vc, m, x0, iy0, ix0, iy1, 0f, border);
            quad(vc, m, ix1, iy0, x1, iy1, 0f, border);
        }

        // Fill + track side by side (also non-overlapping).
        float fillEnd = ix0 + (ix1 - ix0) * ratio;
        if (ratio > 0.002f) {
            quad(vc, m, ix0, iy0, fillEnd, iy1, 0f, 0xFF000000 | gradient(ratio, c));
        }
        if (fillEnd < ix1) {
            int alpha = (int) (c.backgroundOpacity * 255f) & 0xFF;
            quad(vc, m, fillEnd, iy0, ix1, iy1, 0f, (alpha << 24) | (c.cTrack & 0xFFFFFF));
        }

        // Absorption is drawn as a separate gold strip on top of the bar, never as normal health.
        if (c.showAbsorption && t.absRatio > 0f) {
            float aw = (ix1 - ix0) * t.absRatio;
            float ah = (iy1 - iy0) / 3f;
            quad(vc, m, ix0, iy0, ix0 + aw, iy0 + ah, Z_OVERLAY, 0xFF000000 | (c.cAbs & 0xFFFFFF));
        }

        // Optional number, to the right of the bar (no overlap with the quads above).
        if (c.showNumeric) {
            Font font = Minecraft.getInstance().font;
            ps.pushPose();
            ps.translate(half + 3f, top + c.height * 0.5f, Z_OVERLAY);
            ps.scale(0.75f, 0.75f, 1f);
            font.drawInBatch(c.debug ? t.numericText + " " + Math.round(ratio * 100f) + "%" : t.numericText,
                    0f, -4f, 0xFF000000 | (c.cText & 0xFFFFFF), false, ps.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            ps.popPose();
        }

        ps.popPose();
    }

    /** Counter-clockwise on screen (this space is y-flipped), so it survives back-face culling. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
                             float z, int argb) {
        vc.addVertex(m, x0, y0, z).setColor(argb);
        vc.addVertex(m, x0, y1, z).setColor(argb);
        vc.addVertex(m, x1, y1, z).setColor(argb);
        vc.addVertex(m, x1, y0, z).setColor(argb);
    }

    /** Full -> mid at 100%..50%, mid -> low at 50%..0%. Uses the displayed (animated) ratio. */
    private static int gradient(float r, ModConfig c) {
        if (r >= 0.5f) return lerp(c.cMid, c.cFull, (r - 0.5f) * 2f);
        return lerp(c.cLow, c.cMid, r * 2f);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
