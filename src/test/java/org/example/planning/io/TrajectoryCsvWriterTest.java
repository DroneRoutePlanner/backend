package org.example.planning.io;

import org.example.environment.Vector3d;
import org.example.planning.RouteEvaluationResult;
import org.example.planning.SimulationTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryCsvWriterTest {

    @Test
    void writesHeaderAndRows(@TempDir Path tmp) throws Exception {
        List<List<Vector3d>> frames = List.of(
                List.of(new Vector3d(0, 0, 1), new Vector3d(10, 10, 2)),
                List.of(new Vector3d(1, 0, 1), new Vector3d(9, 10, 2))
        );
        var result = new RouteEvaluationResult(1, 1, 0, 0, true);
        SimulationTrace trace = new SimulationTrace(result, frames);

        Path out = tmp.resolve("t.csv");
        TrajectoryCsvWriter.write(trace, out);

        String text = Files.readString(out);
        assertTrue(text.startsWith("step,drone_id,x,y,z\n"));
        assertTrue(text.contains("0,0,0,0,1"));
        assertTrue(text.contains("0,1,10,10,2"));
        assertTrue(text.contains("1,0,1,0,1"));
    }
}
