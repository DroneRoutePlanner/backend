package org.example.planning.model;

import org.example.Terrain;
import org.example.environment.WindField;

import java.util.Collections;
import java.util.List;

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

    public boolean isNoFly(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height || noFly[x][y];
    }

    public int scaledGroundLevel(int x, int y) {
        int raw = terrain.getHeight(x, y);
        int scaled = raw * maxAltitude / 100;
        return Math.min(maxAltitude, Math.max(0, scaled));
    }

    public List<RadarStation> getRadars() {
        return Collections.unmodifiableList(radars);
    }

    public WindField getWindField() {
        return windField;
    }
}
