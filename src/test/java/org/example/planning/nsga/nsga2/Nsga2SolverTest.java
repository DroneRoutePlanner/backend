package org.example.planning.nsga.nsga2;

import org.example.Terrain;
import org.example.planning.Individual;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class Nsga2SolverTest {

    @Test
    void nsga2ProducesNonEmptyFirstFront() {
        Terrain terrain = new Terrain(12, 12);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);
        Nsga2Solver solver = new Nsga2Solver(12345L);
        List<Individual> pareto = solver.run(problem, 24, 20);
        assertFalse(pareto.isEmpty());
    }
}
