package org.example.ui;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.example.Direction;
import org.example.Drone;
import org.example.Terrain;

public class MapVisualizer {

    private final int widthTiles;

    private final int heightTiles;

    private final int tileSize;

    private final Drone drone;

    private final Pane root;

    private Circle droneView;

    private final Terrain terrain;

    public MapVisualizer(int widthTiles, int heightTiles, int tileSize, Drone drone, Terrain terrain) {
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tileSize = tileSize;
        this.drone = drone;
        this.terrain = terrain;

        root = new Pane();
        root.setPrefSize(widthTiles * tileSize, heightTiles * tileSize);

        drawTerrain(root);
        drawGrid(root);
        createDroneView(root);
    }

    public Pane getRoot() {
        return root;
    }

    private void drawTerrain(Pane root) {
        for (int y = 0; y < heightTiles; y++) {
            for (int x = 0; x < widthTiles; x++) {
                Rectangle cell = new Rectangle(tileSize, tileSize);
                cell.setX(x * tileSize);
                cell.setY((heightTiles - 1 - y) * tileSize); // odwrócenie Y

                int z = terrain.getHeight(x, y);
                cell.setFill(colorForHeight(z));

                root.getChildren().add(cell);
            }
        }
    }

    private Color colorForHeight(int z) {
        // Upewniamy się, że z jest w przedziale 0-100 i unikamy dzielenia intów
        double ratio = Math.max(0, Math.min(100, z)) / 100.0;

        if (ratio < 0.4) {
            // DOLINY: od soczystej zieleni do ciemniejszej zieleni/oliwki
            // (0.0 = ForestGreen, 0.4 = YellowGreen/Yellow)
            return Color.FORESTGREEN.interpolate(Color.YELLOWGREEN, ratio / 0.4);
        } else if (ratio < 0.7) {
            // WZGÓRZA: od zielono-żółtego do wyraźnego żółtego/piaskowego
            return Color.YELLOWGREEN.interpolate(Color.GOLD, (ratio - 0.4) / 0.3);
        } else {
            // GÓRY: od żółtego/złotego do ciemnego brązu
            // (0.7 = Gold, 1.0 = SaddleBrown)
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

    private void createDroneView(Pane root) {
        droneView = new Circle(tileSize / 3.0, Color.DODGERBLUE);
        updateDroneView();
        root.getChildren().add(droneView);
    }

    public void updateDroneView() {
        droneView.setCenterX(
                drone.getPosition().getX() * tileSize + tileSize / 2.0
        );

        // odwrócenie osi Y
        droneView.setCenterY(
                (heightTiles - 1 - drone.getPosition().getY()) * tileSize + tileSize / 2.0
        );
    }

    // opcjonalnie możesz mieć metodę do przesuwania drona
    public void moveDrone(Direction direction) {
        drone.move(direction);
        updateDroneView();
    }
}
