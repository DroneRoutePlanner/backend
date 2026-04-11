package org.example.planning;

import org.example.environment.Vector3d;
import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;
import org.example.planning.model.RadarStation;

import java.util.ArrayList;
import java.util.List;

public final class MultiDroneRouteEvaluator {

    private static final double PENALTY_NOT_GOAL = 2_000.0;

    private static final double PENALTY_COLLISION = 8_000.0;

    private static final double PENALTY_OOB = 3_000.0;

    private static final double PENALTY_GROUND = 5_000.0;

    private static final double PENALTY_NO_FLY = 6_000.0;

    private static final double PENALTY_ENERGY = 50.0;

    private static final double HORIZ_ENERGY = 1.0;

    private static final double VERT_ENERGY = 1.6;

    private static final double CLIMB_EXTRA = 0.35;

    public RouteEvaluationResult evaluate(PlanningProblem problem, int[][] genes) {
        return simulate(problem, genes, false).result();
    }

    /**
     * Pełna symulacja; przy {@code recordTimeline} zwraca listę klatek (początek = pozycje startowe).
     */
    public SimulationTrace simulate(PlanningProblem problem, int[][] genes, boolean recordTimeline) {
        PlanningContext ctx = problem.context();
        List<DroneMission> missions = problem.missions();
        int dCount = missions.size();
        int maxT = problem.maxStepsPerDrone();

        List<Vector3d> pos = new ArrayList<>(dCount);
        List<Boolean> arrived = new ArrayList<>(dCount);
        List<Integer> arrivalTime = new ArrayList<>(dCount);
        List<Double> energyAcc = new ArrayList<>(dCount);

        for (DroneMission m : missions) {
            pos.add(m.start());
            arrived.add(false);
            arrivalTime.add(-1);
            energyAcc.add(0.0);
        }

        List<List<Vector3d>> timeline = recordTimeline ? new ArrayList<>() : null;
        if (recordTimeline) {
            timeline.add(copyPositions(pos));
        }

        double radarSum = 0.0;
        double violation = 0.0;

        for (int t = 0; t < maxT; t++) {
            double time = t + ctx.getEvaluationTime();
            List<Vector3d> next = new ArrayList<>(dCount);
            for (int d = 0; d < dCount; d++) {
                next.add(pos.get(d));
            }

            for (int d = 0; d < dCount; d++) {
                if (arrived.get(d)) {
                    continue;
                }
                Vector3d from = pos.get(d);
                int code = genes[d][t];
                Vector3d delta = MoveEncoding.delta(code);
                Vector3d to = from.add(delta);

                int x = to.getX();
                int y = to.getY();
                int z = to.getZ();

                if (x < 0 || x >= ctx.getWidth() || y < 0 || y >= ctx.getHeight()
                        || z < 0 || z > ctx.getMaxAltitude()) {
                    violation += PENALTY_OOB;
                    continue;
                }

                if (ctx.isNoFly(x, y)) {
                    violation += PENALTY_NO_FLY;
                }

                int ground = ctx.scaledGroundLevel(x, y);
                if (z < ground) {
                    violation += PENALTY_GROUND;
                }

                double stepEnergy = stepEnergy(from, to);
                double newE = energyAcc.get(d) + stepEnergy;
                energyAcc.set(d, newE);

                DroneMission mission = missions.get(d);
                if (newE > mission.energyBudget()) {
                    violation += (newE - mission.energyBudget()) * PENALTY_ENERGY;
                }

                next.set(d, to);

                double risk = radarRiskAt(ctx.getRadars(), x + 0.5, y + 0.5, z + 0.5, time);
                radarSum += risk;

                if (reachedGoal(ctx, to, mission)) {
                    arrived.set(d, true);
                    arrivalTime.set(d, t + 1);
                }
            }

            for (int a = 0; a < dCount; a++) {
                for (int b = a + 1; b < dCount; b++) {
                    if (next.get(a).equals(next.get(b))) {
                        violation += PENALTY_COLLISION;
                    }
                }
            }

            pos = next;
            if (recordTimeline) {
                timeline.add(copyPositions(pos));
            }
        }

        double makespan = 0.0;
        double totalEnergy = 0.0;
        for (int d = 0; d < dCount; d++) {
            totalEnergy += energyAcc.get(d);
            if (Boolean.TRUE.equals(arrived.get(d))) {
                makespan = Math.max(makespan, arrivalTime.get(d));
            } else {
                DroneMission m = missions.get(d);
                int dist = goalDistance(ctx, pos.get(d), m);
                violation += dist * PENALTY_NOT_GOAL;
                makespan = Math.max(makespan, maxT + dist);
            }
        }

        boolean feasible = violation < 1e-6;
        RouteEvaluationResult result = new RouteEvaluationResult(makespan, totalEnergy, radarSum, violation, feasible);
        List<List<Vector3d>> frames = recordTimeline ? timeline : List.of();
        return new SimulationTrace(result, frames);
    }

    private static List<Vector3d> copyPositions(List<Vector3d> positions) {
        return new ArrayList<>(positions);
    }

    private static double stepEnergy(Vector3d from, Vector3d to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = to.getZ() - from.getZ();
        double horiz = (dx + dy) * HORIZ_ENERGY;
        double vert = Math.abs(dz) * VERT_ENERGY;
        double climb = dz > 0 ? dz * CLIMB_EXTRA : 0.0;
        return horiz + vert + climb;
    }

    private static double radarRiskAt(List<RadarStation> radars, double x, double y, double z, double time) {
        double s = 0.0;
        for (RadarStation r : radars) {
            s += r.detectionRisk(x, y, z, time);
        }
        return s;
    }

    private static boolean reachedGoal(PlanningContext ctx, Vector3d p, DroneMission m) {
        Vector3d g = m.goal();
        if (p.getX() != g.getX() || p.getY() != g.getY()) {
            return false;
        }
        int ground = ctx.scaledGroundLevel(g.getX(), g.getY());
        int zMin = ground;
        int zMax = ctx.getMaxAltitude();
        return p.getZ() >= zMin && p.getZ() <= zMax;
    }

    private static int goalDistance(PlanningContext ctx, Vector3d p, DroneMission m) {
        Vector3d g = m.goal();
        int dxy = Math.abs(p.getX() - g.getX()) + Math.abs(p.getY() - g.getY());
        int preferredZ = Math.min(g.getZ(), ctx.getMaxAltitude());
        int dz = Math.abs(p.getZ() - preferredZ);
        return dxy + dz;
    }
}
