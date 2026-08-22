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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bezokienkowe porównanie NSGA-II, NSGA-III i MOGWO na tych samych scenariuszach z tym samym
 * budżetem (populacja × iteracje). Dla każdego uruchomienia fronty wszystkich algorytmów są
 * normalizowane do wspólnych granic (ideal/nadir sumy frontów) i oceniane hiperobjętością
 * z punktem referencyjnym {@code 1 + margin}.
 * <p>
 * Argumenty {@code klucz=wartość}: {@code pop} (100), {@code iter} (100), {@code runs} (3),
 * {@code seed} (42), {@code width}/{@code height} (30), {@code margin} (0.1),
 * {@code out} (csv/comparison — prefiks plików *_summary.csv i *_fronts.csv).
 */
public final class AlgorithmComparison {

    private record RunResult(int run, SolverKind kind, List<Individual> front, long millis,
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
        int width = intOption(options, "width", 30);
        int height = intOption(options, "height", 30);
        double margin = Double.parseDouble(options.getOrDefault("margin", "0.1"));
        Path outPrefix = Path.of(options.getOrDefault("out", "csv/comparison"));

        System.out.printf(Locale.ROOT, "Porównanie: pop=%d iter=%d runs=%d seed=%d mapa=%dx%d%n",
                populationSize, iterations, runs, baseSeed, width, height);

        List<RunResult> results = new ArrayList<>();
        for (int run = 0; run < runs; run++) {
            long scenarioSeed = baseSeed + run;
            Terrain terrain = new Terrain(width, height, scenarioSeed);
            PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);

            Map<SolverKind, List<Individual>> fronts = new EnumMap<>(SolverKind.class);
            Map<SolverKind, Long> times = new EnumMap<>(SolverKind.class);
            for (SolverKind kind : SolverKind.values()) {
                MultiObjectiveSolver solver = kind.create(scenarioSeed * 31 + kind.ordinal());
                final int runIndex = run;
                long start = System.nanoTime();
                List<Individual> front = solver.run(problem, populationSize, iterations, (iteration, nonDominated) -> {
                    if ((iteration + 1) % Math.max(1, iterations / 10) == 0 || iteration == iterations - 1) {
                        System.out.printf(Locale.ROOT, "  [%s] run %d  iteracja %d/%d  front=%d%n",
                                kind.displayName(), runIndex + 1, iteration + 1, iterations, nonDominated.size());
                    }
                });
                times.put(kind, (System.nanoTime() - start) / 1_000_000L);
                fronts.put(kind, front);
            }

            ObjectiveBounds bounds = ObjectiveBounds.ofFronts(fronts.values());
            double maxHv = Hypervolume.maxNormalized(margin);
            for (SolverKind kind : SolverKind.values()) {
                double hv = Hypervolume.normalized(fronts.get(kind), bounds, margin);
                results.add(new RunResult(run, kind, fronts.get(kind), times.get(kind), hv, hv / maxHv));
            }
            printRunTable(run, results);
        }

        printSummary(results);
        writeSummaryCsv(results, Path.of(outPrefix + "_summary.csv"));
        writeFrontsCsv(results, Path.of(outPrefix + "_fronts.csv"));
        System.out.printf("Zapisano %s_summary.csv i %s_fronts.csv%n", outPrefix, outPrefix);
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
