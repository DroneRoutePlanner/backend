package org.example.bench;

import org.example.Terrain;
import org.example.planning.Individual;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.metrics.Hypervolume;
import org.example.planning.metrics.ObjectiveBounds;
import org.example.planning.solver.MultiObjectiveSolver;
import org.example.planning.solver.SolverKind;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bezokienkowe porównanie NSGA-II, NSGA-III i MOGWO na tych samych scenariuszach z tym samym
 * budżetem (populacja × iteracje). Dla każdego uruchomienia fronty wszystkich algorytmów są
 * normalizowane do wspólnych granic (ideal/nadir sumy frontów końcowych) i oceniane hiperobjętością
 * z punktem referencyjnym {@code 1 + margin}. W tych samych granicach oceniane są też migawki zbioru
 * niezdominowanego zapisywane w trakcie optymalizacji (krzywe zbieżności).
 * <p>
 * Argumenty {@code klucz=wartość}: {@code pop} (100), {@code iter} (100), {@code runs} (3),
 * {@code seed} (42 — baza ziaren algorytmów), {@code terrainSeed} (= seed — ziarno jednej, stałej
 * instancji terenu wspólnej dla wszystkich uruchomień), {@code width}/{@code height} (30), {@code margin} (0.1),
 * {@code checkpoints} (50 — liczba punktów krzywej zbieżności),
 * {@code out} (csv/comparison — prefiks plików *_summary.csv, *_fronts.csv, *_convergence.csv).
 */
public final class AlgorithmComparison {

    private record RunResult(int run, SolverKind kind, List<Individual> front, long millis,
                             double hypervolume, double hypervolumeRatio) {
    }

    private record Snapshot(SolverKind kind, int iteration, List<double[]> objectives) {
    }

    private record ConvergenceRow(int run, SolverKind kind, int iteration, int frontSize,
                                  double hypervolume, double hypervolumeRatio) {
    }

    private AlgorithmComparison() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parseOptions(args);
        int populationSize = intOption(options, "pop", 100);
        int iterations = intOption(options, "iter", 100);
        int runs = intOption(options, "runs", 3);
        long baseSeed = Long.parseLong(options.getOrDefault("seed", "42"));
        long terrainSeed = Long.parseLong(options.getOrDefault("terrainSeed", Long.toString(baseSeed)));
        int width = intOption(options, "width", 30);
        int height = intOption(options, "height", 30);
        double margin = Double.parseDouble(options.getOrDefault("margin", "0.1"));
        int checkpoints = intOption(options, "checkpoints", 50);
        Path outPrefix = Path.of(options.getOrDefault("out", "csv/comparison"));

        int checkpointStep = Math.max(1, iterations / checkpoints);
        double maxHv = Hypervolume.maxNormalized(margin);

        System.out.printf(Locale.ROOT, "Porównanie: pop=%d iter=%d runs=%d seed=%d terrainSeed=%d mapa=%dx%d%n",
                populationSize, iterations, runs, baseSeed, terrainSeed, width, height);

        // Jedna, stała instancja problemu dla wszystkich uruchomień i algorytmów —
        // między runami zmienia się wyłącznie ziarno generatora losowego algorytmu.
        Terrain terrain = new Terrain(width, height, terrainSeed);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);

        List<RunResult> results = new ArrayList<>();
        List<ConvergenceRow> convergence = new ArrayList<>();
        for (int run = 0; run < runs; run++) {
            long runSeed = baseSeed + run;

            Map<SolverKind, List<Individual>> fronts = new EnumMap<>(SolverKind.class);
            Map<SolverKind, Long> times = new EnumMap<>(SolverKind.class);
            List<Snapshot> snapshots = new ArrayList<>();
            for (SolverKind kind : SolverKind.values()) {
                MultiObjectiveSolver solver = kind.create(runSeed * 31 + kind.ordinal());
                final int runIndex = run;
                long start = System.nanoTime();
                List<Individual> front = solver.run(problem, populationSize, iterations, (iteration, nonDominated) -> {
                    boolean last = iteration == iterations - 1;
                    if ((iteration + 1) % checkpointStep == 0 || last) {
                        snapshots.add(new Snapshot(kind, iteration + 1, copyObjectives(nonDominated)));
                    }
                    if ((iteration + 1) % Math.max(1, iterations / 10) == 0 || last) {
                        System.out.printf(Locale.ROOT, "  [%s] run %d  iteracja %d/%d  front=%d%n",
                                kind.displayName(), runIndex + 1, iteration + 1, iterations, nonDominated.size());
                    }
                });
                times.put(kind, (System.nanoTime() - start) / 1_000_000L);
                fronts.put(kind, front);
            }

            ObjectiveBounds bounds = ObjectiveBounds.ofFronts(fronts.values());
            double[] reference = new double[Individual.OBJECTIVE_COUNT];
            Arrays.fill(reference, 1.0 + margin);
            for (SolverKind kind : SolverKind.values()) {
                double hv = Hypervolume.normalized(fronts.get(kind), bounds, margin);
                results.add(new RunResult(run, kind, fronts.get(kind), times.get(kind), hv, hv / maxHv));
            }
            for (Snapshot snapshot : snapshots) {
                List<double[]> normalized = new ArrayList<>(snapshot.objectives().size());
                for (double[] point : snapshot.objectives()) {
                    normalized.add(bounds.normalize(point));
                }
                double hv = Hypervolume.of(normalized, reference);
                convergence.add(new ConvergenceRow(run, snapshot.kind(), snapshot.iteration(),
                        snapshot.objectives().size(), hv, hv / maxHv));
            }
            printRunTable(run, results);
        }

        printSummary(results);
        writeSummaryCsv(results, Path.of(outPrefix + "_summary.csv"));
        writeFrontsCsv(results, Path.of(outPrefix + "_fronts.csv"));
        writeConvergenceCsv(convergence, Path.of(outPrefix + "_convergence.csv"));
        System.out.printf("Zapisano %s_summary.csv, %s_fronts.csv i %s_convergence.csv%n",
                outPrefix, outPrefix, outPrefix);
    }

    private static List<double[]> copyObjectives(List<Individual> front) {
        List<double[]> copy = new ArrayList<>(front.size());
        for (Individual individual : front) {
            copy.add(individual.getObjectives().clone());
        }
        return copy;
    }

    private static void printRunTable(int run, List<RunResult> results) {
        System.out.printf(Locale.ROOT, "%n--- Run %d ---%n%-9s | %6s | %10s | %8s | %8s%n",
                run + 1, "Algorytm", "|front|", "HV", "HV/max", "czas[ms]");
        for (RunResult r : results) {
            if (r.run() == run) {
                System.out.printf(Locale.ROOT, "%-9s | %6d | %10.5f | %8.4f | %8d%n",
                        r.kind().displayName(), r.front().size(), r.hypervolume(), r.hypervolumeRatio(), r.millis());
            }
        }
    }

    private static void printSummary(List<RunResult> results) {
        System.out.printf(Locale.ROOT, "%n=== Średnia ± odch. std. HV/max po uruchomieniach ===%n");
        for (SolverKind kind : SolverKind.values()) {
            List<Double> ratios = new ArrayList<>();
            for (RunResult r : results) {
                if (r.kind() == kind) {
                    ratios.add(r.hypervolumeRatio());
                }
            }
            double mean = ratios.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            double variance = ratios.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
            System.out.printf(Locale.ROOT, "%-9s  %.4f ± %.4f  (n=%d)%n", kind.displayName(), mean, Math.sqrt(variance), ratios.size());
        }
    }

    private static void writeSummaryCsv(List<RunResult> results, Path path) throws IOException {
        createParent(path);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("run,algorithm,front_size,hypervolume,hypervolume_ratio,time_ms");
            w.newLine();
            for (RunResult r : results) {
                w.write(String.format(Locale.ROOT, "%d,%s,%d,%.6f,%.6f,%d",
                        r.run(), r.kind().displayName(), r.front().size(), r.hypervolume(), r.hypervolumeRatio(), r.millis()));
                w.newLine();
            }
        }
    }

    private static void writeFrontsCsv(List<RunResult> results, Path path) throws IOException {
        createParent(path);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("run,algorithm,makespan,energy,radar_risk");
            w.newLine();
            for (RunResult r : results) {
                for (Individual individual : r.front()) {
                    w.write(String.format(Locale.ROOT, "%d,%s,%.6f,%.6f,%.6f",
                            r.run(), r.kind().displayName(),
                            individual.objective(Individual.MAKESPAN),
                            individual.objective(Individual.ENERGY),
                            individual.objective(Individual.RADAR_RISK)));
                    w.newLine();
                }
            }
        }
    }

    private static void writeConvergenceCsv(List<ConvergenceRow> rows, Path path) throws IOException {
        createParent(path);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("run,algorithm,iteration,front_size,hypervolume,hypervolume_ratio");
            w.newLine();
            for (ConvergenceRow r : rows) {
                w.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%.6f,%.6f",
                        r.run(), r.kind().displayName(), r.iteration(), r.frontSize(), r.hypervolume(), r.hypervolumeRatio()));
                w.newLine();
            }
        }
    }

    private static void createParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Oczekiwano klucz=wartość, otrzymano: " + arg);
            }
            options.put(arg.substring(0, eq).trim().toLowerCase(Locale.ROOT), arg.substring(eq + 1).trim());
        }
        return options;
    }

    private static int intOption(Map<String, String> options, String key, int defaultValue) {
        return Integer.parseInt(options.getOrDefault(key, Integer.toString(defaultValue)));
    }
}
