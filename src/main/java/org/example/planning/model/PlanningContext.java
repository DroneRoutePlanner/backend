package org.example.planning.model;

import org.example.Terrain;
import org.example.environment.Vector3d;
import org.example.environment.WindField;

import java.util.List;

/** Statyczne środowisko planowania: rozmiar siatki, pułap, teren, strefy zakazu lotu, radary, wiatr. */
public final class PlanningContext {

    private final int width;

    private final int height;

    private final int maxAltitude;

    private final Terrain terrain;

    private final boolean[][] noFly;

    private final List<RadarStation> radars;

    private final WindField windField;

    public PlanningContext(
            int width,
            int height,
            int maxAltitude,
            Terrain terrain,
            boolean[][] noFly,
            List<RadarStation> radars,
            WindField windField
    ) {
        if (terrain.getWidth() != width || terrain.getDepth() != height) {
            throw new IllegalArgumentException("Rozmiar terenu nie zgadza się z rozmiarem siatki");
        }
        this.width = width;
        this.height = height;
        this.maxAltitude = maxAltitude;
        this.terrain = terrain;
        this.noFly = noFly;
        this.radars = List.copyOf(radars);
        this.windField = windField;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxAltitude() {
        return maxAltitude;
    }

    public Terrain getTerrain() {
        return terrain;
    }

    public boolean isInside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean isNoFly(int x, int y) {
        return !isInside(x, y) || noFly[x][y];
    }

    /** Poziom terenu przeskalowany z zakresu surowego 0–100 do [0, maxAltitude]. */
    public int scaledGroundLevel(int x, int y) {
        return scaledGroundLevel(terrain, maxAltitude, x, y);
    }

    public static int scaledGroundLevel(Terrain terrain, int maxAltitude, int x, int y) {
        int scaled = terrain.getHeight(x, y) * maxAltitude / Terrain.MAX_RAW_HEIGHT;
        return Math.min(maxAltitude, Math.max(0, scaled));
    }

    /**
     * Czy dron może znajdować się w komórce {@code p}: wewnątrz mapy, w przedziale pułapu
     * [poziom terenu, maxAltitude] i poza strefą zakazu lotu.
     */
    public boolean isTraversable(Vector3d p) {
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        return isInside(x, y)
                && z >= 0 && z <= maxAltitude
                && !noFly[x][y]
                && z >= scaledGroundLevel(x, y);
    }

    public List<RadarStation> getRadars() {
        return radars;
    }

    public WindField getWindField() {
        return windField;
    }
}
