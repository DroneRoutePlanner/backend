package org.example.planning;

import org.example.environment.Vector3d;
import org.example.environment.WindField;
import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;
import org.example.planning.model.RadarStation;

import java.util.ArrayList;
import java.util.List;

/**
 * Symulator tras zespołu dronów i funkcja celu (trzy minimalizowane kryteria):
 * makespan, łączna energia, łączne ryzyko wykrycia przez radary.
 * <p>
 * Model czasu: drony poruszają się naprzemiennie (round-robin) — w ticku {@code t} rusza się dron
 * {@code t mod n}, zużywając kolejny gen swojej trasy. Każdy dron ma dokładnie
 * {@code maxStepsPerDrone} tur, więc horyzont wynosi {@code maxStepsPerDrone * n} ticków.
 * <p>
 * Ograniczenia (granice mapy, pułap, teren, strefy zakazu lotu, kolizje między dronami) są
 * egzekwowane twardo: ruch naruszający ograniczenie jest odrzucany — dron pozostaje w miejscu,
 * a gen jest zużyty. Dzięki temu kryteria nie zawierają sztucznych kar, a każde rozwiązanie jest
 * dopuszczalne przestrzennie. Dron, który nie dotarł do celu, otrzymuje makespan równy horyzontowi
 * powiększonemu o pozostałą odległość do celu.
 * <p>
 * Klasa jest bezstanowa — jedną instancję można współdzielić między wątkami.
 */
public final class MultiDroneRouteEvaluator {

    private static final double HORIZONTAL_ENERGY = 1.0;

    private static final double VERTICAL_ENERGY = 1.6;

    private static final double CLIMB_EXTRA = 0.35;

    private static final double WIND_OPPOSITION_COST = 0.65;

    private static final double RADAR_RISK_MULTIPLIER = 10.0;

    private static final double RADAR_ZONE_STEP_PENALTY = 150.0;

    public RouteEvaluationResult evaluate(PlanningProblem problem, int[][] genes) {
        return simulate(problem, genes, false).result();
    }

    /**
     * @param recordTimeline czy zapisywać pozycje wszystkich dronów po każdym ticku (do animacji / CSV)
     */
    public SimulationTrace simulate(PlanningProblem problem, int[][] genes, boolean recordTimeline) {
        PlanningContext ctx = problem.context();
        List<DroneMission> missions = problem.missions();
        int droneCount = missions.size();
        int maxSteps = problem.maxStepsPerDrone();
        int tickLimit = maxSteps * droneCount;

        Vector3d[] positions = new Vector3d[droneCount];
        boolean[] arrived = new boolean[droneCount];
        int[] arrivalTick = new int[droneCount];
        double[] energy = new double[droneCount];
        int[] nextGene = new int[droneCount];
        for (int d = 0; d < droneCount; d++) {
            positions[d] = missions.get(d).start();
        }

        List<List<Vector3d>> timeline = recordTimeline ? new ArrayList<>(tickLimit + 1) : null;
        if (recordTimeline) {
            timeline.add(List.of(positions.clone()));
        }

        double radarRisk = 0.0;
        int finishedDrones = 0;

        for (int t = 0; t < tickLimit && finishedDrones < droneCount; t++) {
            int d = t % droneCount;
            if (!arrived[d] && nextGene[d] < maxSteps) {
                Vector3d from = positions[d];
                Vector3d to = from.add(MoveEncoding.delta(genes[d][nextGene[d]++]));

                if (ctx.isTraversable(to) && !isOccupied(positions, d, to)) {
                    energy[d] += stepEnergy(from, to, ctx.getWindField().windAt(t));
                    radarRisk += radarStepCost(radarRiskAt(ctx.getRadars(), to));
                    positions[d] = to;
                    if (reachedGoal(to, missions.get(d))) {
                        arrived[d] = true;
                        arrivalTick[d] = t + 1;
                        finishedDrones++;
                    }
                }
                if (!arrived[d] && nextGene[d] >= maxSteps) {
                    finishedDrones++;
                }
            }
            if (recordTimeline) {
                timeline.add(List.of(positions.clone()));
            }
        }

        double makespan = 0.0;
        double totalEnergy = 0.0;
        for (int d = 0; d < droneCount; d++) {
            totalEnergy += energy[d];
            double droneTime = arrived[d]
                    ? arrivalTick[d]
                    : tickLimit + remainingDistance(ctx, positions[d], missions.get(d));
            makespan = Math.max(makespan, droneTime);
        }

        RouteEvaluationResult result = new RouteEvaluationResult(makespan, totalEnergy, radarRisk);
        return new SimulationTrace(result, recordTimeline ? timeline : List.of());
    }

    private static boolean isOccupied(Vector3d[] positions, int movingDrone, Vector3d cell) {
        for (int d = 0; d < positions.length; d++) {
            if (d != movingDrone && positions[d].equals(cell)) {
                return true;
            }
        }
        return false;
    }

    private static double stepEnergy(Vector3d from, Vector3d to, WindField.Wind wind) {
        int mx = to.getX() - from.getX();
        int my = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        double horizontal = (Math.abs(mx) + Math.abs(my)) * HORIZONTAL_ENERGY;
        double vertical = Math.abs(dz) * VERTICAL_ENERGY;
        double climb = dz > 0 ? dz * CLIMB_EXTRA : 0.0;
        double cost = horizontal + vertical + climb;

        double alongWind = mx * wind.x() + my * wind.y();
        if (alongWind < 0) {
            cost += WIND_OPPOSITION_COST * -alongWind;
        }
        return cost;
    }

    private static double radarStepCost(double risk) {
        return risk <= 0.0 ? 0.0 : risk * RADAR_RISK_MULTIPLIER + RADAR_ZONE_STEP_PENALTY;
    }

    /** Ryzyko w środku komórki (x+0.5, y+0.5, z+0.5) — suma po wszystkich radarach. */
    private static double radarRiskAt(List<RadarStation> radars, Vector3d cell) {
        double sum = 0.0;
        for (RadarStation radar : radars) {
            sum += radar.detectionRisk(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
        }
        return sum;
    }

    /** Cel uznaje się za osiągnięty po dotarciu nad komórkę celu (dowolna dopuszczalna wysokość). */
    private static boolean reachedGoal(Vector3d p, DroneMission mission) {
        return p.getX() == mission.goal().getX() && p.getY() == mission.goal().getY();
    }

    private static int remainingDistance(PlanningContext ctx, Vector3d p, DroneMission mission) {
        Vector3d goal = mission.goal();
        int goalZ = Math.min(goal.getZ(), ctx.getMaxAltitude());
        return p.chebyshevDistance(new Vector3d(goal.getX(), goal.getY(), goalZ));
    }
}
