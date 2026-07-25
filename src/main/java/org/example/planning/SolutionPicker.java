package org.example.planning;

import java.util.Comparator;
import java.util.List;

public final class SolutionPicker {

    private SolutionPicker() {
    }

    /**
     * Wybiera z pierwszego frontu Pareto osobnik o najmniejszym makespanie; przy
     * remisie
     * mniejsze ryzyko radarowe ({@code objectives[2]}), potem mniejsza suma energii
     * ({@code objectives[1]}).
     */
    public static Individual pickSolution(List<Individual> paretoFirstFront) {

        // --- CZYTELNE WYPISANIE LISTY ---
        System.out.println("\n=================== PIERWSZY FRONT PARETO ===================");
        System.out.printf("%-5s | %-12s | %-12s | %-12s%n", "Nr", "Czas", "Energia", "Ryzyko");
        System.out.println("-------------------------------------------------------------");

        for (int i = 0; i < paretoFirstFront.size(); i++) {
            Individual ind = paretoFirstFront.get(i);
            double[] objectives = ind.getObjectives();

            // Zakładamy, że tablica objectives ma przynajmniej 3 elementy
            System.out.printf("#%-4d | %-12.4f | %-12.4f | %-12.4f%n",
                    (i + 1), objectives[0], objectives[1], objectives[2]);
        }
        System.out.println("=============================================================\n");
        // ---------------------------------

        if (paretoFirstFront.isEmpty()) {
            throw new IllegalArgumentException("Pusta lista Pareto");
        }
        return paretoFirstFront.stream()
                .min(Comparator.comparingDouble((Individual individual) -> individual.getObjectives()[0])
                        .thenComparingDouble(individual -> individual.getObjectives()[2])
                        .thenComparingDouble(individual -> individual.getObjectives()[1]))
                .orElseThrow();
    }
}
