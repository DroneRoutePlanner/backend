package org.example.planning;

import org.example.environment.Vector3d;

import java.util.List;

public record SimulationTrace(
        RouteEvaluationResult result,
        List<List<Vector3d>> positionsPerStep
) {
    public SimulationTrace {
        positionsPerStep = List.copyOf(positionsPerStep);
    }
}
