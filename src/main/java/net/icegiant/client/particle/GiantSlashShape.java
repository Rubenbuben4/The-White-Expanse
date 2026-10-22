package net.icegiant.client.particle;

/**
 * Where Rubenbuben4's slash picture lies around the swinger (GiantSlashParticle). No Minecraft classes, so
 * test/GiantSlashShapeTest can check it.
 * <p>
 * His 9 pictures (64 x 47, saved as 64 x 48 with an empty last row) together draw one slash ring: it starts at the
 * top left, runs over the top to the right and back along the bottom to the left. The ring is an ellipse (centre
 * 31.2 / 19.3, half axes 28.8 x 18.3 pixels, measured from his sheet): a flat circle drawn at a slant. So the
 * picture is laid flat (waagerecht) with the swinger in the centre of the ring and the short axis stretched
 * until the ring is a circle again:
 * <ul>
 * <li>right in the picture = straight ahead of the swinger (the thick part of the slash is in front),</li>
 * <li>up in the picture = the swinger's left, down = his right.</li>
 * </ul>
 * Seen from above it then runs left - front - right, like the sword in his animation (it comes from the left,
 * 0.5 s: about 95 degrees left of ahead, and ends on the right, 0.9 s: about 65 degrees right). It is fixed to the
 * swinger's body, not turned to whoever looks at it.
 */
public final class GiantSlashShape {

    public static final float PICTURE_W = 64.0f, PICTURE_H = 48.0f;
    /** Centre and half axes of the slash ring in the picture (pixels). */
    public static final float CENTER_U = 31.22f, CENTER_V = 19.28f, HALF_U = 28.79f, HALF_V = 18.30f;

    private GiantSlashShape() {
    }

    /**
     * World offset (x, z) from the swinger of a point (u, v) of the picture (pixels), for a swinger looking along
     * yaw (degrees, Minecraft: 0 = south) and a ring of this radius (blocks).
     */
    public static double[] offset(double u, double v, float yawDegrees, double radius) {
        double ahead = (u - CENTER_U) / HALF_U * radius;
        double left = (CENTER_V - v) / HALF_V * radius;
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        // ahead = (-sin, cos), left = (cos, sin) - the left hand of someone looking south (+z) points east (+x)
        return new double[]{-sin * ahead + cos * left, cos * ahead + sin * left};
    }

    /** The 4 corners of the picture (u, v in pixels), in the order the particle draws them. */
    public static final float[][] CORNERS = {{0, 0}, {PICTURE_W, 0}, {PICTURE_W, PICTURE_H}, {0, PICTURE_H}};
}
