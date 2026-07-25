package org.example.planning;

import org.example.Terrain;
import org.example.environment.Vector3d;
import org.example.environment.WindField;
import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;
import org.example.planning.model.RadarStation;

import java.util.ArrayList;
import java.util.List;

public final class PlanningScenarioFactory {

    private static final int MAX_ALTITUDE = 12;

    private PlanningScenarioFactory() {
    }

    public static PlanningProblem defaultMultiDrone(Terrain terrain) {
        int w = terrain.getWidth();
        int h = terrain.getHeightMapHeight();

        int g0x = Math.max(1, w - 2);
        int g0y = Math.max(1, h - 2);
        int s1x = Math.max(1, w - 2);
        int s1y = Math.min(2, h - 2);
        int g1x = 2;
        int g1y = Math.max(1, h - 2);
        int s2x = Math.min(3, w - 2);
        int s2y = Math.max(1, h - 3);
        int g2x = Math.max(1, w - 2);
        int g2y = Math.min(3, h - 1);

        int[][] endpoints = {
                { 1, 1 }, { g0x, g0y },
                { s1x, s1y }, { g1x, g1y },
                { s2x, s2y }, { g2x, g2y }
        };

        boolean[][] noFly = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (terrain.getHeight(x, y) >= 96 && !isEndpoint(x, y, endpoints)) {
                    noFly[x][y] = true;
                }
            }
        }

        List<RadarStation> radars = List.of(
                new RadarStation(w * 0.28, h * 0.52, 5.0, Math.max(2.5, w * 0.20), 14.0),
                new RadarStation(w * 0.72, h * 0.32, 4.0, Math.max(2.5, w * 0.18), 12.0),
                new RadarStation(w * 0.52, h * 0.72, 6.0, Math.max(2.0, w * 0.16), 10.0));

        WindField wind = new WindField(0.55, -0.4);

        PlanningContext context = new PlanningContext(
                w,
                h,
                MAX_ALTITUDE,
                terrain,
                noFly,
                radars,
                wind);

        List<DroneMission> missions = new ArrayList<>();
        missions.add(mission(terrain, 0, 1, 1, g0x, g0y, 240));
        missions.add(mission(terrain, 1, s1x, s1y, g1x, g1y, 230));
        missions.add(mission(terrain, 2, s2x, s2y, g2x, g2y, 235));

        int maxSteps = Math.max(160, (w + h) * 5);
        return new PlanningProblem(context, missions, maxSteps);
    }

    private static DroneMission mission(
            Terrain terrain,
            int id,
            int sx,
            int sy,
            int gx,
            int gy,
            double energyBudget) {
        int sz = safeAltitude(terrain, sx, sy);
        int gz = safeAltitude(terrain, gx, gy);
        return new DroneMission(
                id,
                new Vector3d(sx, sy, sz),
                new Vector3d(gx, gy, gz),
                energyBudget);
    }

    private static int safeAltitude(Terrain terrain, int x, int y) {
        int raw = terrain.getHeight(x, y);
        int scaled = raw * MAX_ALTITUDE / 100;
        scaled = Math.min(MAX_ALTITUDE, Math.max(0, scaled));
        return Math.min(MAX_ALTITUDE, scaled + 1);
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
