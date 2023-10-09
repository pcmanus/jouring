package io.github.pcmanus.jouring.bench;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IOUringBatchedAsyncEngineTest {

    @Test
    void clearAll() {
        List<String> strs = new ArrayList<>(List.of("a", "b", "c", "d", "e", "f", "g"));

        int[] toClear = new int[] { 1, 3, 6, 0, 2, 4, 5 };
        IOUringBatchedAsyncEngine.clearAll(strs, toClear, 3);
        assertEquals(strs, List.of("a", "c", "e", "f"));
    }
}