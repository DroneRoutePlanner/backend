package org.example.planning;

import java.util.Comparator;
import java.util.List;

/** Wybór jednego rozwiązania z frontu Pareto do wizualizacji (decyzja a posteriori). */
public final class SolutionPicker {

    private static final Comparator<Individual> LOWEST_RISK_THEN_TIME_THEN_ENERGY =
            Comparator.comparingDouble((Individual i) -> i.objective(Individual.RADAR_RISK))
                    .thenComparingDouble(i -> i.objective(Individual.MAKESPAN))
                    .thenComparingDouble(i -> i.objective(Individual.ENERGY));

    private SolutionPicker() {
    }

    /**
     * Zwraca osobnik o najmniejszym ryzyku radarowym; przy remisie mniejszy makespan,
     * potem mniejsza energia.
     */
    public static Individual pickSolution(List<Individual> paretoFront) {
        if (paretoFront.isEmpty()) {
            throw new IllegalArgumentException("Pusty front Pareto");
        }
        return paretoFront.stream().min(LOWEST_RISK_THEN_TIME_THEN_ENERGY).orElseThrow();
    }
}
