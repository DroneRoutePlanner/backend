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
import org.example.planning.nsga2.Individual;
import org.example.planning.nsga2.Nsga2Solver;
import org.example.ui.MapVisualizer;

import java.util.ArrayList;
import java.util.List;

public class World extends Application {

    private static final int TILE_SIZE = 40;

    private static final int WIDTH = 20;

    private static final int HEIGHT = 20;

    private static final double RADAR_UI_TIME = 0.0;

    private List<Drone> drones;

    private MapVisualizer mapVisualizer;

    private Label statusLabel;

    @Override
    public void start(Stage stage) {

        Terrain terrain = new Terrain(WIDTH, HEIGHT);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain, RADAR_UI_TIME);
        drones = dronesFromMissions(problem);

        mapVisualizer = new MapVisualizer(
                WIDTH,
                HEIGHT,
                TILE_SIZE,
                drones,
                terrain,
                problem,
                RADAR_UI_TIME
        );

        String planner = System.getProperty("drone.planner", "nsga2").toLowerCase();
        boolean useGwo = "gwo".equals(planner);
        statusLabel = new Label(useGwo ? "GWO: przygotowanie optymalizacji…" : "NSGA-II: przygotowanie optymalizacji…");
        statusLabel.setPadding(new Insets(10));
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(WIDTH * TILE_SIZE);
        statusLabel.setStyle("-fx-font-size: 13px;");

        BorderPane rootPane = new BorderPane();
        rootPane.setCenter(mapVisualizer.getRoot());
        rootPane.setBottom(statusLabel);

        Scene scene = new Scene(rootPane, WIDTH * TILE_SIZE, HEIGHT * TILE_SIZE + 72);
        stage.setTitle(useGwo ? "GWO — planowanie trasy zespołu dronów" : "NSGA-II — planowanie trasy zespołu dronów");
        stage.setScene(scene);
        stage.show();

        runPlanningAndAnimate(problem, useGwo);
    }

    private static List<Drone> dronesFromMissions(PlanningProblem problem) {
        List<Drone> list = new ArrayList<>();
        for (DroneMission m : problem.missions()) {
            list.add(new Drone(m.id(), m.start(), m.energyBudget(), m.maxSpeedCellsPerTick()));
        }
        return List.copyOf(list);
    }

    private void runPlanningAndAnimate(PlanningProblem problem, boolean useGwo) {
        final int populationSize = 56;
        final int generations = 100;

        new Thread(() -> {
            try {
                MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();
                Individual chosen;

                if (useGwo) {
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
                                        ms
                                ));
                            })
                    );
                } else {
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
                                        ms
                                ));
                            })
                    );
                    chosen = PlanningSolutionPicker.pickForReplay(pareto);
                }

                SimulationTrace trace = evaluator.simulate(problem, chosen.getGenes(), true);

                Platform.runLater(() -> {
                    updateStatusAfterSolve(trace, useGwo);
                    startPathAnimation(trace.positionsPerStep());
                });
            } catch (Exception e) {
                String tag = useGwo ? "GWO" : "NSGA-II";
                Platform.runLater(() -> statusLabel.setText("Błąd " + tag + ": " + e.getMessage()));
            }
        }, useGwo ? "gwo-ui" : "nsga2-ui").start();
    }

    private void updateStatusAfterSolve(SimulationTrace trace, boolean fromGwo) {
        var r = trace.result();
        String feasNote = r.isFeasible()
                ? "tak"
                : (fromGwo ? "nie (najlepszy wilk α wg skalaryzacji)" : "nie (najlepsza z Pareto wg kar)");
        statusLabel.setText(String.format(
                "Wybrano trasę do animacji | dopuszczalna: %s | czas zespołu=%.1f | energia=%.1f | ryzyko radarów=%.2f",
                feasNote,
                r.getMakespan(),
                r.getTotalEnergy(),
                r.getTotalRadarRisk()
        ));
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
