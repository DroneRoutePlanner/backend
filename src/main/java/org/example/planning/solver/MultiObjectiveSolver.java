package org.example.planning.solver;

import org.example.planning.Individual;
import org.example.planning.PlanningProblem;

import java.util.List;

/**
 * Wspólny kontrakt algorytmów wielokryterialnych (NSGA-II, NSGA-III, MOGWO). Każdy algorytm
 * pracuje na tym samym kodowaniu ({@link org.example.planning.MoveEncoding}), tym samym
 * ewaluatorze i tym samym budżecie ({@code populationSize} ocen na iterację), a zwraca zbiór
 * rozwiązań wzajemnie niezdominowanych — dzięki czemu wyniki można porównywać np. hiperobjętością.
 */
public interface MultiObjectiveSolver {

    String name();

    /**
     * @param populationSize rozmiar populacji (NSGA) / watahy i archiwum (MOGWO)
     * @param iterations     liczba pokoleń / iteracji
     * @return front Pareto (kopie osobników; wzajemnie niezdominowane)
     */
    List<Individual> run(PlanningProblem problem, int populationSize, int iterations, IterationListener listener);

    default List<Individual> run(PlanningProblem problem, int populationSize, int iterations) {
        return run(problem, populationSize, iterations, IterationListener.NONE);
    }
}
