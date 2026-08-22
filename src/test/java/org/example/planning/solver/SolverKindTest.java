package org.example.planning.solver;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolverKindTest {

    @Test
    void parsesAliasesCaseInsensitively() {
        assertEquals(Optional.of(SolverKind.GWO), SolverKind.parse(" GWO "));
        assertEquals(Optional.of(SolverKind.NSGA_II), SolverKind.parse("nsga-II"));
        assertEquals(Optional.of(SolverKind.NSGA_III), SolverKind.parse("nsga3"));
        assertEquals(Optional.empty(), SolverKind.parse("pso"));
        assertEquals(Optional.empty(), SolverKind.parse(null));
    }
}
