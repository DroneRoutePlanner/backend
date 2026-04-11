package org.example.planning.model;

import org.example.environment.Vector3d;

public record DroneMission(
        int id,
        Vector3d start,
        Vector3d goal,
        double energyBudget,
        double maxSpeedCellsPerTick
) {
}
