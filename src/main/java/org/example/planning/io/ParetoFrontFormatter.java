package org.example.planning.io;

import org.example.planning.Individual;

import java.util.List;

/** Tekstowa tabela frontu Pareto do logów konsolowych. */
public final class ParetoFrontFormatter {

    private ParetoFrontFormatter() {
    }

    public static String format(String title, List<Individual> front) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n=== %s (%d rozwiązań) ===%n", title, front.size()));
        sb.append(String.format("%-5s | %-12s | %-12s | %-12s%n", "Nr", "Czas", "Energia", "Ryzyko"));
        sb.append("------------------------------------------------------\n");
        for (int i = 0; i < front.size(); i++) {
            Individual individual = front.get(i);
            sb.append(String.format("#%-4d | %-12.3f | %-12.3f | %-12.3f%n",
                    i + 1,
                    individual.objective(Individual.MAKESPAN),
                    individual.objective(Individual.ENERGY),
                    individual.objective(Individual.RADAR_RISK)));
        }
        return sb.toString();
    }
}
