package org.example.planning;

import org.example.planning.nsga2.Individual;

import java.util.Comparator;
import java.util.List;

public final class PlanningSolutionPicker {

    private PlanningSolutionPicker() {
    }

    public static Individual pickForReplay(List<Individual> paretoFront) {
        if (paretoFront.isEmpty()) {
            throw new IllegalArgumentException("Pusta lista Pareto");
        }
        return paretoFront.stream()
                .filter(Individual::isFeasible)
                .min(Comparator.comparingDouble(i -> i.getObjectives()[0]))
                .orElseGet(() -> paretoFront.stream()
                        .min(Comparator.comparingDouble(Individual::getConstraintViolation))
                        .orElse(paretoFront.get(0)));
    }
}
