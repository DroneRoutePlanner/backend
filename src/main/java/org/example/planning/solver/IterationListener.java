package org.example.planning.solver;

import org.example.planning.Individual;

import java.util.List;

/** Obserwator postępu optymalizacji (np. pasek statusu UI, logowanie metryk w czasie). */
@FunctionalInterface
public interface IterationListener {

    IterationListener NONE = (iteration, nonDominated) -> {
    };

    /**
     * @param iteration    numer iteracji/pokolenia (od 0)
     * @param nonDominated bieżący zbiór niezdominowany (tylko do odczytu, nie modyfikować)
     */
    void onIteration(int iteration, List<Individual> nonDominated);
}
