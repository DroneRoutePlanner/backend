package org.example.planning;

import org.example.planning.model.DroneMission;
import org.example.planning.model.PlanningContext;

import java.util.List;

/**
 * Instancja problemu: środowisko, misje dronów oraz długość chromosomu (liczba kroków na drona).
 */
public record PlanningProblem(
        PlanningContext context,
        List<DroneMission> missions,
        int maxStepsPerDrone
) {
    public PlanningProblem {
        if (missions.isEmpty()) {
            throw new IllegalArgumentException("Problem musi zawierać co najmniej jedną misję");
        }
        if (maxStepsPerDrone < 1) {
            throw new IllegalArgumentException("maxStepsPerDrone musi być dodatnie");
        }
        missions = List.copyOf(missions);
    }

    public int droneCount() {
        return missions.size();
    }
}
