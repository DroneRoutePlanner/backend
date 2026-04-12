package org.example.planning.nsga.nsga3;

import org.example.Terrain;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.nsga.Individual;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class Nsga3SolverTest {

    @Test
    void nsga3ProducesNonEmptyFirstFront() {
        Terrain terrain = new Terrain(12, 12);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);
        Nsga3Solver solver = new Nsga3Solver(12345L);
        List<Individual> pareto = solver.run(problem, 24, 20);
        assertFalse(pareto.isEmpty());
    }
}
