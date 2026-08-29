package org.example.bench;

import org.example.Terrain;
import org.example.planning.Individual;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.SimulationTrace;
import org.example.planning.SolutionPicker;
import org.example.planning.io.ParetoFrontFormatter;
import org.example.planning.io.TerrainCsvWriter;
import org.example.planning.io.TrajectoryCsvWriter;
import org.example.planning.solver.MultiObjectiveSolver;
import org.example.planning.solver.SolverKind;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bezokienkowy odpowiednik uruchomienia z UI: jeden algorytm na jednej instancji, wybór trasy
 * regułą {@link SolutionPicker} i zapis trajektorii oraz terenu do CSV (do wykresu 3D w Pythonie).
 * Ziarna identyczne jak w {@link AlgorithmComparison}: teren {@code seed}, algorytm
 * {@code seed * 31 + indeks algorytmu} — rysunek odpowiada więc dokładnie instancji z porównania.
 * <p>
 * Argumenty: {@code planner=nsga2|nsga3|gwo seed=42 pop=100 iter=500 width=30 height=30 out=csv/trasa.csv}.
 */
public final class TrajectoryExport {

    private TrajectoryExport() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            options.put(arg.substring(0, eq).trim().toLowerCase(Locale.ROOT), arg.substring(eq + 1).trim());
        }
        SolverKind kind = SolverKind.parse(options.getOrDefault("planner", "nsga2"))
                .orElseThrow(() -> new IllegalArgumentException("Nieznany algorytm: " + options.get("planner")));
        long seed = Long.parseLong(options.getOrDefault("seed", "42"));
        int populationSize = Integer.parseInt(options.getOrDefault("pop", "100"));
        int iterations = Integer.parseInt(options.getOrDefault("iter", "500"));
        int width = Integer.parseInt(options.getOrDefault("width", "30"));
        int height = Integer.parseInt(options.getOrDefault("height", "30"));
        Path out = Path.of(options.getOrDefault("out", "csv/trasa.csv"));

        Terrain terrain = new Terrain(width, height, seed);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);
        MultiObjectiveSolver solver = kind.create(seed * 31 + kind.ordinal());

        long start = System.nanoTime();
        List<Individual> front = solver.run(problem, populationSize, iterations);
        long millis = (System.nanoTime() - start) / 1_000_000L;

        Individual chosen = SolutionPicker.pickSolution(front, problem);
        SimulationTrace trace = new MultiDroneRouteEvaluator().simulate(problem, chosen.getGenes(), true);
        TrajectoryCsvWriter.write(trace, out);
        Path terrainPath = TerrainCsvWriter.siblingTerrainPath(out);
        TerrainCsvWriter.write(terrain, terrainPath);

        System.out.print(ParetoFrontFormatter.format("Front Pareto " + solver.name() + " (seed " + seed + ")", front));
        System.out.printf(Locale.ROOT, "Wybrano: makespan=%.0f energia=%.1f ryzyko=%.1f | czas %d ms | horyzont %d tików%n",
                trace.result().makespan(), trace.result().totalEnergy(), trace.result().totalRadarRisk(),
                millis, problem.maxStepsPerDrone() * problem.droneCount());
        System.out.printf("Zapisano %s i %s%n", out, terrainPath);
    }
}
