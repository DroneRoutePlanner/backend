package org.example.ui;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.example.Drone;
import org.example.Terrain;
import org.example.planning.PlanningProblem;
import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;
import org.example.planning.model.RadarStation;

import java.util.ArrayList;
import java.util.List;

/** Widok 2D mapy (rzut z góry): teren, strefy radarów, cele misji i pozycje dronów. */
public final class MapVisualizer {

    private static final Color[] DRONE_COLORS = {
            Color.DODGERBLUE, Color.CRIMSON, Color.DARKORANGE, Color.MEDIUMPURPLE, Color.LIMEGREEN
    };

    private final int widthTiles;

    private final int heightTiles;

    private final int tileSize;

    private final List<Drone> drones;

    private final Pane root;

    private final List<Circle> droneViews = new ArrayList<>();

    private final Terrain terrain;

    public MapVisualizer(
            int widthTiles,
            int heightTiles,
            int tileSize,
            List<Drone> drones,
            Terrain terrain,
            PlanningProblem planningOverlay
    ) {
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tileSize = tileSize;
        this.drones = List.copyOf(drones);
        this.terrain = terrain;

        root = new Pane();
        root.setPrefSize(widthTiles * tileSize, heightTiles * tileSize);

        drawTerrain(root);
        if (planningOverlay != null) {
            drawPlanningOverlay(root, planningOverlay);
        }
        drawGrid(root);
        createDroneViews(root);
    }

    public Pane getRoot() {
        return root;
    }

    private void drawTerrain(Pane root) {
        for (int y = 0; y < heightTiles; y++) {
            for (int x = 0; x < widthTiles; x++) {
                Rectangle cell = new Rectangle(tileSize, tileSize);
                cell.setX(x * tileSize);
                cell.setY((heightTiles - 1 - y) * tileSize);

                int z = terrain.getHeight(x, y);
                cell.setFill(colorForHeight(z));

                root.getChildren().add(cell);
            }
        }
    }

    private void drawPlanningOverlay(Pane root, PlanningProblem problem) {
        PlanningContext ctx = problem.context();
        for (RadarStation radar : ctx.getRadars()) {
            double cx = radar.getX() * tileSize + tileSize / 2.0;
            double cy = (heightTiles - 1 - radar.getY()) * tileSize + tileSize / 2.0;
            double rPx = radar.getInfluenceRadius() * tileSize;
            Circle zone = new Circle(rPx);
            zone.setCenterX(cx);
            zone.setCenterY(cy);
            zone.setFill(Color.color(1, 0.2, 0.2, 0.14));
            zone.setStroke(Color.color(0.6, 0, 0, 0.55));
            zone.setStrokeWidth(1.5);
            zone.setMouseTransparent(true);
            root.getChildren().add(zone);
        }

        List<DroneMission> missions = problem.missions();
        for (int i = 0; i < missions.size(); i++) {
            DroneMission m = missions.get(i);
            var g = m.goal();
            Rectangle marker = new Rectangle(tileSize - 6, tileSize - 6);
            marker.setFill(Color.TRANSPARENT);
            marker.setStroke(DRONE_COLORS[i % DRONE_COLORS.length]);
            marker.setStrokeWidth(2.5);
            marker.setArcWidth(6);
            marker.setArcHeight(6);
            marker.setX(g.getX() * tileSize + 3);
            marker.setY((heightTiles - 1 - g.getY()) * tileSize + 3);
            marker.setMouseTransparent(true);
            root.getChildren().add(marker);
        }
    }

    private Color colorForHeight(int z) {
        double ratio = Math.max(0, Math.min(100, z)) / 100.0;

        if (ratio < 0.4) {
            return Color.FORESTGREEN.interpolate(Color.YELLOWGREEN, ratio / 0.4);
        } else if (ratio < 0.7) {
            return Color.YELLOWGREEN.interpolate(Color.GOLD, (ratio - 0.4) / 0.3);
        } else {
            return Color.GOLD.interpolate(Color.SADDLEBROWN, (ratio - 0.7) / 0.3);
        }
    }

    private void drawGrid(Pane root) {
        for (int x = 0; x <= widthTiles; x++) {
            Line line = new Line(
                    x * tileSize, 0,
                    x * tileSize, heightTiles * tileSize
            );
            line.setStroke(Color.LIGHTGRAY);
            root.getChildren().add(line);
        }

        for (int y = 0; y <= heightTiles; y++) {
            Line line = new Line(
                    0, y * tileSize,
                    widthTiles * tileSize, y * tileSize
            );
            line.setStroke(Color.LIGHTGRAY);
            root.getChildren().add(line);
        }
    }

    private void createDroneViews(Pane root) {
        for (int i = 0; i < drones.size(); i++) {
            Circle c = new Circle(tileSize / 3.0, DRONE_COLORS[i % DRONE_COLORS.length]);
            droneViews.add(c);
            root.getChildren().add(c);
        }
        updateDroneViews();
    }

    public void updateDroneViews() {
        for (int i = 0; i < drones.size(); i++) {
            Drone drone = drones.get(i);
            Circle droneView = droneViews.get(i);
            droneView.setCenterX(
                    drone.getPosition().getX() * tileSize + tileSize / 2.0
            );
            droneView.setCenterY(
                    (heightTiles - 1 - drone.getPosition().getY()) * tileSize + tileSize / 2.0
            );
        }
    }
}
