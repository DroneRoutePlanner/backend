package org.example.planning.gwo;

import org.example.Terrain;
import org.example.planning.Individual;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.SolutionPicker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GwoSolverTest {

    @Test
    void mogwoReturnsParetoFrontAndPickableSolution() {
        Terrain terrain = new Terrain(12, 12);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain);
        GwoSolver solver = new GwoSolver(12345L);
        List<Individual> pareto = solver.run(problem, 24, 20);
        assertFalse(pareto.isEmpty());
        Individual chosen = SolutionPicker.pickSolution(pareto);
        assertNotNull(chosen);
        assertNotNull(chosen.getGenes());
    }
}
