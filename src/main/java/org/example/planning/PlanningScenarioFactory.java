package org.example.planning;

import org.example.Terrain;
import org.example.environment.Vector3d;
import org.example.environment.WindField;
import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;
import org.example.planning.model.RadarStation;

import java.util.ArrayList;
import java.util.List;

/** Buduje domyślny scenariusz testowy: 3 drony, 3 radary na powierzchni terenu, stały wiatr z podmuchami. */
public final class PlanningScenarioFactory {

    public static final int MAX_ALTITUDE = 20;

    /** Komórki o surowej wysokości ≥ tej wartości (szczyty) są strefą zakazu lotu. */
    private static final int NO_FLY_RAW_HEIGHT = 96;

    private static final int MIN_STEPS_PER_DRONE = 160;

    private static final WindField DEFAULT_WIND = new WindField(0.55, -0.4);

    private PlanningScenarioFactory() {
    }

    public static PlanningProblem defaultMultiDrone(Terrain terrain) {
        int w = terrain.getWidth();
        int h = terrain.getDepth();

        int[][] startXY = {
                { 1, 1 },
                { Math.max(1, w - 2), Math.min(2, h - 2) },
                { Math.min(3, w - 2), Math.max(1, h - 3) }
        };
        int[][] goalXY = {
                { Math.max(1, w - 2), Math.max(1, h - 2) },
                { 2, Math.max(1, h - 2) },
                { Math.max(1, w - 2), Math.min(3, h - 1) }
        };

        boolean[][] noFly = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                noFly[x][y] = terrain.getHeight(x, y) >= NO_FLY_RAW_HEIGHT
                        && !isEndpoint(x, y, startXY) && !isEndpoint(x, y, goalXY);
            }
        }

        List<RadarStation> radars = List.of(
                radarOnSurface(terrain, w * 0.28, h * 0.52, Math.max(2.5, w * 0.20), 14.0),
                radarOnSurface(terrain, w * 0.72, h * 0.32, Math.max(2.5, w * 0.18), 12.0),
                radarOnSurface(terrain, w * 0.52, h * 0.72, Math.max(2.0, w * 0.16), 10.0));
        PlanningContext context = new PlanningContext(w, h, MAX_ALTITUDE, terrain, noFly, radars, DEFAULT_WIND);

        double[] energyBudgets = { 240, 230, 235 };
        List<DroneMission> missions = new ArrayList<>();
        for (int id = 0; id < startXY.length; id++) {
            missions.add(new DroneMission(
                    id,
                    safePosition(context, startXY[id][0], startXY[id][1]),
                    safePosition(context, goalXY[id][0], goalXY[id][1]),
                    energyBudgets[id]));
        }

        int maxSteps = Math.max(MIN_STEPS_PER_DRONE, (w + h) * 5);
        return new PlanningProblem(context, missions, maxSteps);
    }

    /** Radar stojący na powierzchni terenu w (x, y). */
    private static RadarStation radarOnSurface(
            Terrain terrain, double x, double y, double influenceRadius, double strength) {
        int ix = clamp((int) Math.round(x), 0, terrain.getWidth() - 1);
        int iy = clamp((int) Math.round(y), 0, terrain.getDepth() - 1);
        double z = PlanningContext.scaledGroundLevel(terrain, MAX_ALTITUDE, ix, iy);
        return new RadarStation(x, y, z, influenceRadius, strength);
    }

    /** Pozycja jedną jednostkę nad terenem (nie wyżej niż pułap). */
    private static Vector3d safePosition(PlanningContext ctx, int x, int y) {
        int z = Math.min(ctx.getMaxAltitude(), ctx.scaledGroundLevel(x, y) + 1);
        return new Vector3d(x, y, z);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static boolean isEndpoint(int x, int y, int[][] endpoints) {
        for (int[] e : endpoints) {
            if (e[0] == x && e[1] == y) {
                return true;
            }
        }
        return false;
    }
}
