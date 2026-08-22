package org.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.example.environment.Vector3d;
import org.example.planning.Individual;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.SimulationTrace;
import org.example.planning.SolutionPicker;
import org.example.planning.io.ParetoFrontFormatter;
import org.example.planning.io.TerrainCsvWriter;
import org.example.planning.io.TrajectoryCsvWriter;
import org.example.planning.model.DroneMission;
import org.example.planning.solver.MultiObjectiveSolver;
import org.example.planning.solver.SolverKind;
import org.example.ui.MapVisualizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplikacja JavaFX: generuje scenariusz, uruchamia wybrany algorytm w tle i animuje wybraną trasę.
 * Punktem wejścia procesu jest {@link Launcher}.
 * <p>
 * Właściwości systemowe: {@code drone.planner} (gwo | nsga2 | nsga3, domyślnie nsga3),
 * {@code drone.seed} (ziarno terenu i algorytmu; domyślnie losowe),
 * {@code drone.trajectory.csv} (ścieżka zapisu trajektorii; obok powstaje *_terrain.csv).
 */
public class World extends Application {

    private static final int TILE_SIZE = 10;

    private static final int WIDTH = 30;

    private static final int HEIGHT = 30;

    private static final int POPULATION_SIZE = 1000;

    private static final int ITERATIONS = 1000;

    private static final long FRAME_INTERVAL_NS = 95_000_000L;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    private List<Drone> drones;

    private MapVisualizer mapVisualizer;

    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        long seed = parseSeed(System.getProperty("drone.seed"));
        SolverKind kind = SolverKind.parse(System.getProperty("drone.planner")).orElse(SolverKind.NSGA_III);

        Terrain terrain = new Terrain(WIDTH, HEIGHT, seed);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);
        drones = dronesFromMissions(problem);
        mapVisualizer = new MapVisualizer(WIDTH, HEIGHT, TILE_SIZE, drones, terrain, problem);

        statusLabel = new Label(kind.displayName() + ": przygotowanie optymalizacji…");
        statusLabel.setPadding(new Insets(10));
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(WIDTH * TILE_SIZE);
        statusLabel.setStyle("-fx-font-size: 13px;");

        BorderPane rootPane = new BorderPane();
        rootPane.setCenter(mapVisualizer.getRoot());
        rootPane.setBottom(statusLabel);

        stage.setTitle(kind.displayName() + " — planowanie trasy zespołu dronów (seed " + seed + ")");
        stage.setScene(new Scene(rootPane, WIDTH * TILE_SIZE, HEIGHT * TILE_SIZE + 72));
        stage.show();

        runPlanningAndAnimate(problem, kind, seed);
    }

    private static long parseSeed(String raw) {
        if (raw == null || raw.isBlank()) {
            return System.nanoTime();
        }
        return Long.parseLong(raw.trim());
    }

    private static List<Drone> dronesFromMissions(PlanningProblem problem) {
        List<Drone> list = new ArrayList<>();
        for (DroneMission mission : problem.missions()) {
            list.add(new Drone(mission.id(), mission.start()));
        }
        return List.copyOf(list);
    }

    private void runPlanningAndAnimate(PlanningProblem problem, SolverKind kind, long seed) {
        MultiObjectiveSolver solver = kind.create(seed);
        Thread worker = new Thread(() -> {
            try {
                List<Individual> front = solver.run(problem, POPULATION_SIZE, ITERATIONS, (iteration, nonDominated) -> {
                    double bestMakespan = nonDominated.stream()
                            .mapToDouble(i -> i.objective(Individual.MAKESPAN))
                            .min()
                            .orElse(Double.NaN);
                    String text = String.format("%s: iteracja %d / %d  |  front: %d  |  najl. makespan: %.1f",
                            solver.name(), iteration + 1, ITERATIONS, nonDominated.size(), bestMakespan);
                    Platform.runLater(() -> statusLabel.setText(text));
                });
                System.out.print(ParetoFrontFormatter.format("Front Pareto " + solver.name(), front));

                Individual chosen = SolutionPicker.pickSolution(front);
                SimulationTrace trace = evaluator.simulate(problem, chosen.getGenes(), true);
                String csvNote = exportCsvIfRequested(problem, trace);

                Platform.runLater(() -> {
                    statusLabel.setText(describe(trace) + csvNote);
                    startPathAnimation(trace.positionsPerStep());
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Błąd " + solver.name() + ": " + e.getMessage()));
            }
        }, kind.name().toLowerCase() + "-planner");
        worker.setDaemon(true);
        worker.start();
    }

    private static String exportCsvIfRequested(PlanningProblem problem, SimulationTrace trace) {
        String csvProp = System.getProperty("drone.trajectory.csv");
        if (csvProp == null || csvProp.isBlank()) {
            return "";
        }
        try {
            Path trajectoryPath = Path.of(csvProp.trim());
            TrajectoryCsvWriter.write(trace, trajectoryPath);
            Path terrainPath = TerrainCsvWriter.siblingTerrainPath(trajectoryPath);
            TerrainCsvWriter.write(problem.context().getTerrain(), terrainPath);
            return " | CSV: zapisano " + trajectoryPath + " + " + terrainPath.getFileName();
        } catch (Exception ex) {
            return " | CSV: błąd zapisu — " + ex.getMessage();
        }
    }

    private static String describe(SimulationTrace trace) {
        var r = trace.result();
        return String.format("Wybrano trasę do animacji | czas zespołu=%.1f | energia=%.1f | ryzyko radarów=%.2f",
                r.makespan(), r.totalEnergy(), r.totalRadarRisk());
    }

    private void startPathAnimation(List<List<Vector3d>> frames) {
        if (frames.size() <= 1) {
            statusLabel.setText(statusLabel.getText() + " — za krótka ścieżka do animacji.");
            return;
        }
        AnimationTimer timer = new AnimationTimer() {

            private long lastTickNs;

            private int frameIndex;

            @Override
            public void handle(long nowNs) {
                if (lastTickNs != 0 && nowNs - lastTickNs < FRAME_INTERVAL_NS) {
                    return;
                }
                lastTickNs = nowNs;
                if (frameIndex >= frames.size()) {
                    stop();
                    statusLabel.setText(statusLabel.getText() + " — koniec animacji.");
                    return;
                }
                applyFrame(frames.get(frameIndex++));
            }
        };
        timer.start();
    }

    private void applyFrame(List<Vector3d> snapshot) {
        int n = Math.min(drones.size(), snapshot.size());
        for (int d = 0; d < n; d++) {
            drones.get(d).setPosition(snapshot.get(d));
        }
        mapVisualizer.updateDroneViews();
    }
}
