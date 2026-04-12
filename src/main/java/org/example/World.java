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
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.PlanningSolutionPicker;
import org.example.planning.SimulationTrace;
import org.example.planning.model.DroneMission;
import org.example.planning.gwo.GwoSolver;
import org.example.planning.nsga.Individual;
import org.example.planning.nsga.nsga2.Nsga2Solver;
import org.example.planning.nsga.nsga3.Nsga3Solver;
import org.example.ui.MapVisualizer;

import java.util.ArrayList;
import java.util.List;

public class World extends Application {

    private static final int TILE_SIZE = 40;

    private static final int WIDTH = 20;

    private static final int HEIGHT = 20;

    private List<Drone> drones;

    private MapVisualizer mapVisualizer;

    private Label statusLabel;

    @Override
    public void start(Stage stage) {

        Terrain terrain = new Terrain(WIDTH, HEIGHT);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);
        drones = dronesFromMissions(problem);

        mapVisualizer = new MapVisualizer(
                WIDTH,
                HEIGHT,
                TILE_SIZE,
                drones,
                terrain,
                problem);

        PlannerKind plannerKind = parsePlanner(System.getProperty("drone.planner", "nsga2"));
        statusLabel = new Label(initialStatusLabel(plannerKind));
        statusLabel.setPadding(new Insets(10));
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(WIDTH * TILE_SIZE);
        statusLabel.setStyle("-fx-font-size: 13px;");

        BorderPane rootPane = new BorderPane();
        rootPane.setCenter(mapVisualizer.getRoot());
        rootPane.setBottom(statusLabel);

        Scene scene = new Scene(rootPane, WIDTH * TILE_SIZE, HEIGHT * TILE_SIZE + 72);
        stage.setTitle(windowTitle(plannerKind));
        stage.setScene(scene);
        stage.show();

        runPlanningAndAnimate(problem, plannerKind);
    }

    private enum PlannerKind {
        GWO,
        NSGA_II,
        NSGA_III
    }

    private static PlannerKind parsePlanner(String raw) {
        String p = raw == null ? "" : raw.trim().toLowerCase();
        if (p.contains("gwo")) {
            return PlannerKind.GWO;
        }
        if (p.contains("nsga3") || p.contains("nsga-iii")) {
            return PlannerKind.NSGA_III;
        }
        if (p.contains("nsga2") || p.contains("nsga-ii")) {
            return PlannerKind.NSGA_II;
        }
        return PlannerKind.GWO;
    }

    private static String initialStatusLabel(PlannerKind k) {
        return switch (k) {
            case GWO -> "GWO: przygotowanie optymalizacji…";
            case NSGA_II -> "NSGA-II: przygotowanie optymalizacji…";
            case NSGA_III -> "NSGA-III: przygotowanie optymalizacji…";
        };
    }

    private static String windowTitle(PlannerKind k) {
        return switch (k) {
            case GWO -> "GWO — planowanie trasy zespołu dronów";
            case NSGA_II -> "NSGA-II — planowanie trasy zespołu dronów";
            case NSGA_III -> "NSGA-III — planowanie trasy zespołu dronów";
        };
    }

    private static List<Drone> dronesFromMissions(PlanningProblem problem) {
        List<Drone> list = new ArrayList<>();
        for (DroneMission m : problem.missions()) {
            list.add(new Drone(m.id(), m.start(), m.energyBudget()));
        }
        return List.copyOf(list);
    }

    private void runPlanningAndAnimate(PlanningProblem problem, PlannerKind plannerKind) {
        final int populationSize = 56;
        final int generations = 100;

        new Thread(() -> {
            try {
                MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();
                Individual chosen;

                if (plannerKind == PlannerKind.GWO) {
                    GwoSolver solver = new GwoSolver(System.nanoTime());
                    chosen = solver.run(
                            problem,
                            populationSize,
                            generations,
                            (iter, population) -> Platform.runLater(() -> {
                                long feas = population.stream().filter(Individual::isFeasible).count();
                                double bestMakespan = population.stream()
                                        .filter(Individual::isFeasible)
                                        .mapToDouble(i -> i.getObjectives()[0])
                                        .min()
                                        .orElse(-1);
                                String ms = bestMakespan >= 0 ? String.format("%.1f", bestMakespan) : "—";
                                statusLabel.setText(String.format(
                                        "GWO: iteracja %d / %d  |  dopuszczalne: %d / %d  |  najl. makespan: %s",
                                        iter + 1,
                                        generations,
                                        feas,
                                        populationSize,
                                        ms));
                            }));
                } else if (plannerKind == PlannerKind.NSGA_II) {
                    Nsga2Solver solver = new Nsga2Solver(System.nanoTime());
                    List<Individual> pareto = solver.run(
                            problem,
                            populationSize,
                            generations,
                            (gen, population) -> Platform.runLater(() -> {
                                long feas = population.stream().filter(Individual::isFeasible).count();
                                double bestMakespan = population.stream()
                                        .filter(Individual::isFeasible)
                                        .mapToDouble(i -> i.getObjectives()[0])
                                        .min()
                                        .orElse(-1);
                                String ms = bestMakespan >= 0 ? String.format("%.1f", bestMakespan) : "—";
                                statusLabel.setText(String.format(
                                        "NSGA-II: pokolenie %d / %d  |  dopuszczalne: %d / %d  |  najl. makespan: %s",
                                        gen + 1,
                                        generations,
                                        feas,
                                        populationSize,
                                        ms));
                            }));
                    chosen = PlanningSolutionPicker.pickForReplay(pareto);
                } else {
                    Nsga3Solver solver = new Nsga3Solver(System.nanoTime());
                    List<Individual> pareto = solver.run(
                            problem,
                            populationSize,
                            generations,
                            (gen, population) -> Platform.runLater(() -> {
                                long feas = population.stream().filter(Individual::isFeasible).count();
                                double bestMakespan = population.stream()
                                        .filter(Individual::isFeasible)
                                        .mapToDouble(i -> i.getObjectives()[0])
                                        .min()
                                        .orElse(-1);
                                String ms = bestMakespan >= 0 ? String.format("%.1f", bestMakespan) : "—";
                                statusLabel.setText(String.format(
                                        "NSGA-III: pokolenie %d / %d  |  dopuszczalne: %d / %d  |  najl. makespan: %s",
                                        gen + 1,
                                        generations,
                                        feas,
                                        populationSize,
                                        ms));
                            }));
                    chosen = PlanningSolutionPicker.pickForReplay(pareto);
                }

                SimulationTrace trace = evaluator.simulate(problem, chosen.getGenes(), true);

                Platform.runLater(() -> {
                    updateStatusAfterSolve(trace, plannerKind);
                    startPathAnimation(trace.positionsPerStep());
                });
            } catch (Exception e) {
                String tag = switch (plannerKind) {
                    case GWO -> "GWO";
                    case NSGA_II -> "NSGA-II";
                    case NSGA_III -> "NSGA-III";
                };
                Platform.runLater(() -> statusLabel.setText("Błąd " + tag + ": " + e.getMessage()));
            }
        }, plannerThreadName(plannerKind)).start();
    }

    private static String plannerThreadName(PlannerKind k) {
        return switch (k) {
            case GWO -> "gwo-ui";
            case NSGA_II -> "nsga2-ui";
            case NSGA_III -> "nsga3-ui";
        };
    }

    private void updateStatusAfterSolve(SimulationTrace trace, PlannerKind plannerKind) {
        var r = trace.result();
        String feasNote = r.isFeasible()
                ? "tak"
                : (plannerKind == PlannerKind.GWO
                        ? "nie (najlepszy wilk α wg skalaryzacji)"
                        : "nie (najlepsza z Pareto wg kar)");
        statusLabel.setText(String.format(
                "Wybrano trasę do animacji | dopuszczalna: %s | czas zespołu=%.1f | energia=%.1f | ryzyko radarów=%.2f",
                feasNote,
                r.getMakespan(),
                r.getTotalEnergy(),
                r.getTotalRadarRisk()));
    }

    private void startPathAnimation(List<List<Vector3d>> frames) {
        if (frames.size() <= 1) {
            statusLabel.setText(statusLabel.getText() + " — za krótka ścieżka do animacji.");
            return;
        }

        AnimationTimer timer = new AnimationTimer() {

            private long lastTickNs = 0;

            private int frameIndex = 0;

            @Override
            public void handle(long nowNs) {
                if (lastTickNs == 0) {
                    lastTickNs = nowNs;
                    applyFrame(frames, 0);
                    frameIndex = 1;
                    return;
                }
                if (nowNs - lastTickNs < 95_000_000L) {
                    return;
                }
                lastTickNs = nowNs;
                if (frameIndex >= frames.size()) {
                    stop();
                    Platform.runLater(() -> statusLabel.setText(
                            statusLabel.getText() + " — koniec animacji (możesz uruchomić ponownie)."));
                    return;
                }
                applyFrame(frames, frameIndex);
                frameIndex++;
            }
        };
        timer.start();
    }

    private void applyFrame(List<List<Vector3d>> frames, int index) {
        List<Vector3d> snap = frames.get(index);
        int n = Math.min(drones.size(), snap.size());
        for (int d = 0; d < n; d++) {
            drones.get(d).setPosition(snap.get(d));
        }
        mapVisualizer.updateDroneViews();
    }

    public static void main(String[] args) {
        launch();
    }
}
