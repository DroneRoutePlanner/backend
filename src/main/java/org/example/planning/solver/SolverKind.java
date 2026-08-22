package org.example.planning.solver;

import org.example.planning.gwo.GwoSolver;
import org.example.planning.nsga.nsga2.Nsga2Solver;
import org.example.planning.nsga.nsga3.Nsga3Solver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Dostępne algorytmy wraz z fabryką i parsowaniem nazw z linii poleceń / właściwości systemowych. */
public enum SolverKind {

    GWO("MOGWO", List.of("gwo", "mogwo")),
    NSGA_II("NSGA-II", List.of("nsga2", "nsga-ii", "nsgaii")),
    NSGA_III("NSGA-III", List.of("nsga3", "nsga-iii", "nsgaiii"));

    private final String displayName;

    private final List<String> aliases;

    SolverKind(String displayName, List<String> aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String displayName() {
        return displayName;
    }

    public MultiObjectiveSolver create(long seed) {
        return switch (this) {
            case GWO -> new GwoSolver(seed);
            case NSGA_II -> new Nsga2Solver(seed);
            case NSGA_III -> new Nsga3Solver(seed);
        };
    }

    public static Optional<SolverKind> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (SolverKind kind : values()) {
            if (kind.aliases.contains(key)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
