package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.environment.Vector3d;
import org.example.ui.MapVisualizer;

public class World extends Application {

    private static final int TILE_SIZE = 40;
    private static final int WIDTH = 15;
    private static final int HEIGHT = 15;

    private Drone drone;
    private MapVisualizer mapVisualizer;

    @Override
    public void start(Stage stage) {

        drone = new Drone(new Vector3d(2, 2, 0));

        Terrain terrain = new Terrain(WIDTH, HEIGHT);

        mapVisualizer = new MapVisualizer(WIDTH, HEIGHT, TILE_SIZE, drone, terrain);

        Scene scene = new Scene(mapVisualizer.getRoot(), WIDTH * TILE_SIZE, HEIGHT * TILE_SIZE);
        stage.setTitle("Drone Simulator 2D");
        stage.setScene(scene);
        stage.show();

        simulateMovement();
    }

    private void simulateMovement() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                move(Direction.FORWARD);

                Thread.sleep(1000);
                move(Direction.RIGHT);

                Thread.sleep(1000);
                move(Direction.UP);

            } catch (InterruptedException ignored) { }
        }).start();
    }

    private void move(Direction direction) {
        javafx.application.Platform.runLater(() -> {
            mapVisualizer.moveDrone(direction);
            System.out.println("Drone: " + drone.getPosition());
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
