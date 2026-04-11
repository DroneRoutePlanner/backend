package org.example.planning.gwo;

import org.example.Terrain;
import org.example.planning.PlanningProblem;
import org.example.planning.PlanningScenarioFactory;
import org.example.planning.nsga2.Individual;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GwoSolverTest {

    @Test
    void gwoReturnsAlphaIndividual() {
        Terrain terrain = new Terrain(12, 12);
        PlanningProblem problem = PlanningScenarioFactory.defaultMultiDrone(terrain, 0.0);
        GwoSolver solver = new GwoSolver(12345L);
        Individual alpha = solver.run(problem, 24, 20);
        assertNotNull(alpha);
        assertNotNull(alpha.getGenes());
    }
}
