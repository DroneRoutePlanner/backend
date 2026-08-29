package org.example.planning;

import java.util.Comparator;
import java.util.List;

/**
 * Wybór jednego rozwiązania z frontu Pareto do wizualizacji (decyzja a posteriori).
 * <p>
 * Reguła: spośród rozwiązań, w których wszystkie drony dotarły do celu (makespan nie przekracza
 * horyzontu symulacji), wybierane jest to o najmniejszym ryzyku radarowym; przy remisie mniejszy
 * makespan, potem mniejsza energia. Jeżeli żadne rozwiązanie nie kończy misji w horyzoncie,
 * reguła stosowana jest do całego frontu. Bez preferencji kompletnej misji rozwiązanie o zerowym
 * ryzyku bywa trasą, w której dron w ogóle nie dociera do celu — a wizualizacja sugerowałaby
 * wówczas błąd algorytmu, podczas gdy jest to wyłącznie skutek reguły wyboru.
 */
public final class SolutionPicker {

    private static final Comparator<Individual> LOWEST_RISK_THEN_TIME_THEN_ENERGY =
            Comparator.comparingDouble((Individual i) -> i.objective(Individual.RADAR_RISK))
                    .thenComparingDouble(i -> i.objective(Individual.MAKESPAN))
                    .thenComparingDouble(i -> i.objective(Individual.ENERGY));

    private SolutionPicker() {
    }

    /** Wybór bez informacji o horyzoncie — wyłącznie po ryzyku, makespanie i energii. */
    public static Individual pickSolution(List<Individual> paretoFront) {
        return pickSolution(paretoFront, Double.POSITIVE_INFINITY);
    }

    /**
     * @param horizonTicks horyzont symulacji ({@code maxStepsPerDrone * droneCount}); rozwiązania
     *                     o makespanie nieprzekraczającym horyzontu mają wszystkie drony u celu
     */
    public static Individual pickSolution(List<Individual> paretoFront, double horizonTicks) {
        if (paretoFront.isEmpty()) {
            throw new IllegalArgumentException("Pusty front Pareto");
        }
        List<Individual> complete = paretoFront.stream()
                .filter(i -> i.objective(Individual.MAKESPAN) <= horizonTicks)
                .toList();
        List<Individual> candidates = complete.isEmpty() ? paretoFront : complete;
        return candidates.stream().min(LOWEST_RISK_THEN_TIME_THEN_ENERGY).orElseThrow();
    }

    public static Individual pickSolution(List<Individual> paretoFront, PlanningProblem problem) {
        return pickSolution(paretoFront, (double) problem.maxStepsPerDrone() * problem.droneCount());
    }
}
