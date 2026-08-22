package org.example.planning;

import org.example.Terrain;
import org.example.environment.Vector3d;
import org.example.environment.WindField;
import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiDroneRouteEvaluatorTest {

    private static final int MAX_ALT = 5;

    private static final int PLUS_X = MoveEncoding.codeOf(1, 0, 0);

    private static final int MINUS_X = MoveEncoding.codeOf(-1, 0, 0);

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    private static PlanningProblem problem(Terrain terrain, int steps, DroneMission... missions) {
        int w = terrain.getWidth();
        int h = terrain.getDepth();
        PlanningContext ctx = new PlanningContext(
                w, h, MAX_ALT, terrain, new boolean[w][h], List.of(), new WindField(0, 0));
        return new PlanningProblem(ctx, List.of(missions), steps);
    }

    private static int[] genes(int steps, int... prefix) {
        int[] g = new int[steps];
        Arrays.fill(g, PLUS_X);
        System.arraycopy(prefix, 0, g, 0, prefix.length);
        return g;
    }

    @Test
    void straightFlightReachesGoalWithExpectedTimeAndEnergy() {
        PlanningProblem p = problem(Terrain.flat(8, 3, 0), 6,
                new DroneMission(0, new Vector3d(0, 1, 1), new Vector3d(3, 1, 1), 100));

        RouteEvaluationResult r = evaluator.evaluate(p, new int[][] { genes(6) });

        assertEquals(3.0, r.makespan());
        assertEquals(3.0, r.totalEnergy(), 1e-9);
        assertEquals(0.0, r.totalRadarRisk());
    }

    @Test
    void moveOutsideMapIsRejectedAndDroneStaysInPlace() {
        PlanningProblem p = problem(Terrain.flat(8, 3, 0), 4,
                new DroneMission(0, new Vector3d(0, 1, 1), new Vector3d(3, 1, 1), 100));

        SimulationTrace trace = evaluator.simulate(p, new int[][] { genes(4, MINUS_X) }, true);

        assertEquals(new Vector3d(0, 1, 1), trace.positionsPerStep().get(1).get(0));
        assertEquals(4.0, trace.result().makespan());
        assertEquals(3.0, trace.result().totalEnergy(), 1e-9);
    }

    @Test
    void moveIntoTerrainIsRejected() {
        int[][] map = new int[8][3];
        for (int y = 0; y < 3; y++) {
            map[1][y] = Terrain.MAX_RAW_HEIGHT;
        }
        PlanningProblem p = problem(Terrain.fromHeightMap(map), 3,
                new DroneMission(0, new Vector3d(0, 1, 1), new Vector3d(3, 1, 1), 100));

        SimulationTrace trace = evaluator.simulate(p, new int[][] { genes(3) }, true);

        for (List<Vector3d> frame : trace.positionsPerStep()) {
            assertEquals(new Vector3d(0, 1, 1), frame.get(0), "dron przeleciał przez teren");
        }
        assertTrue(trace.result().makespan() > 3, "nieosiągnięty cel musi wydłużać makespan");
    }

    @Test
    void moveIntoCellOccupiedByOtherDroneIsRejected() {
        PlanningProblem p = problem(Terrain.flat(8, 3, 0), 2,
                new DroneMission(0, new Vector3d(0, 1, 1), new Vector3d(5, 1, 1), 100),
                new DroneMission(1, new Vector3d(1, 1, 1), new Vector3d(5, 0, 1), 100));
        int stay = MoveEncoding.codeOf(0, 0, 1);
        int[][] genes = { genes(2), { MoveEncoding.codeOf(0, 1, 0), stay } };

        SimulationTrace trace = evaluator.simulate(p, genes, true);

        // tick 0: dron 0 próbuje wejść na (1,1,1) zajęte przez drona 1 → zostaje
        assertEquals(new Vector3d(0, 1, 1), trace.positionsPerStep().get(1).get(0));
        // tick 1: dron 1 odlatuje; tick 2: dron 0 może już wejść
        assertEquals(new Vector3d(1, 1, 1), trace.positionsPerStep().get(3).get(0));
    }

    @Test
    void headwindIncreasesEnergy() {
        Terrain terrain = Terrain.flat(8, 3, 0);
        PlanningContext windy = new PlanningContext(
                8, 3, MAX_ALT, terrain, new boolean[8][3], List.of(), new WindField(-1.0, 0));
        PlanningProblem calm = problem(terrain, 3,
                new DroneMission(0, new Vector3d(0, 1, 1), new Vector3d(3, 1, 1), 100));
        PlanningProblem headwind = new PlanningProblem(windy, calm.missions(), 3);

        double calmEnergy = evaluator.evaluate(calm, new int[][] { genes(3) }).totalEnergy();
        double windEnergy = evaluator.evaluate(headwind, new int[][] { genes(3) }).totalEnergy();

        assertTrue(windEnergy > calmEnergy);
    }
}
