package org.example.planning.solver;

import org.example.Terrain;
import org.example.planning.Individual;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.SolutionPicker;
import org.example.planning.pareto.Domination;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wspólne własności wszystkich algorytmów — warunek uczciwego porównania hiperobjętością. */
class SolverContractTest {

    private static PlanningProblem problem() {
        return PlanningScenarioFactory.defaultMultiDrone(new Terrain(12, 12, 7L));
    }

    @ParameterizedTest
    @EnumSource(SolverKind.class)
    void returnsNonEmptyMutuallyNonDominatedFront(SolverKind kind) {
        List<Individual> front = kind.create(12345L).run(problem(), 24, 15);

        assertFalse(front.isEmpty());
        for (Individual a : front) {
            for (Individual b : front) {
                assertFalse(a != b && Domination.dominates(a, b), "front zawiera rozwiązanie zdominowane");
            }
        }
        assertNotNull(SolutionPicker.pickSolution(front).getGenes());
    }

    @ParameterizedTest
    @EnumSource(SolverKind.class)
    void isDeterministicForSeed(SolverKind kind) {
        List<Individual> first = kind.create(99L).run(problem(), 20, 10);
        List<Individual> second = kind.create(99L).run(problem(), 20, 10);
        assertEquals(sortedObjectives(first), sortedObjectives(second));
    }

    @ParameterizedTest
    @EnumSource(SolverKind.class)
    void reportsEveryIterationWithNonDominatedSet(SolverKind kind) {
        List<Integer> seen = new ArrayList<>();
        kind.create(5L).run(problem(), 12, 6, (iteration, nonDominated) -> {
            seen.add(iteration);
            assertFalse(nonDominated.isEmpty());
            for (Individual individual : nonDominated) {
                assertTrue(individual.objective(Individual.MAKESPAN) > 0);
            }
        });
        assertEquals(List.of(0, 1, 2, 3, 4, 5), seen);
    }

    private static List<String> sortedObjectives(List<Individual> front) {
        List<String> rows = new ArrayList<>();
        for (Individual individual : front) {
            rows.add(Arrays.toString(individual.getObjectives()));
        }
        rows.sort(Comparator.naturalOrder());
        return rows;
    }
}
