package org.example.planning;

import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;

import java.util.List;

public record PlanningProblem(
        PlanningContext context,
        List<DroneMission> missions,
        int maxStepsPerDrone
) {
    public int droneCount() {
        return missions.size();
    }
}
