package com.tileman;

import static org.junit.Assert.*;

import java.util.Set;
import org.junit.Test;

public class MovementFlagTest {

    @Test
    public void compress_and_uncompress_result_in_same_flags() {
        int compressed = MovementFlag.compress(MovementFlag.VALUES);
        assertEquals(1 + 2 + 4 + 8 + 16 + 32 + 64 + 128 + 256 + 512 + 1024 + 2048, compressed);
        Set<MovementFlag> uncompressed = MovementFlag.uncompress(compressed);
        for (MovementFlag movementFlag : MovementFlag.VALUES) {
            assertTrue(
                    "Uncompressed does not contain the flag " + movementFlag.name(),
                    uncompressed.contains(movementFlag));
        }
    }
}
