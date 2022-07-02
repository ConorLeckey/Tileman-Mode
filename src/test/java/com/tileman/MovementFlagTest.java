package com.tileman;

import static com.tileman.MovementFlag.NO_MOVEMENT_FLAGS;
import static org.junit.Assert.*;

import java.util.Set;
import org.junit.Test;

public class MovementFlagTest {

    @Test
    public void compress_and_uncompress_result_in_same_flags() {
        int compressed = MovementFlag.compress(MovementFlag.VALUES);
        Set<MovementFlag> uncompressed = MovementFlag.uncompress(compressed);
        for (MovementFlag movementFlag : MovementFlag.VALUES) {
            assertTrue(
                    "Uncompressed does not contain the flag " + movementFlag.name(),
                    uncompressed.contains(movementFlag));
        }
    }

    @Test
    public void compress_methods_does_the_same_thing_one_arg() {
        MovementFlag[] movementFlags = {MovementFlag.BLOCK_MOVEMENT_NORTH_WEST};
        int compressedArr = MovementFlag.compress(movementFlags);
        int compressed = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_NORTH_WEST);
        assertEquals(compressedArr, compressed);
    }

    @Test
    public void compress_methods_does_the_same_thing_two_args() {
        MovementFlag[] movementFlags = {
            MovementFlag.BLOCK_MOVEMENT_NORTH_WEST, MovementFlag.BLOCK_MOVEMENT_NORTH_EAST
        };
        int compressedArr = MovementFlag.compress(movementFlags);
        int compressed =
                MovementFlag.compress(
                        MovementFlag.BLOCK_MOVEMENT_NORTH_WEST,
                        MovementFlag.BLOCK_MOVEMENT_NORTH_EAST);
        assertEquals(compressedArr, compressed);
    }

    @Test
    public void containsAnyOf() {
        int east = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_EAST);
        int west = MovementFlag.compress(MovementFlag.BLOCK_MOVEMENT_WEST);
        int westEast =
                MovementFlag.compress(
                        MovementFlag.BLOCK_MOVEMENT_WEST, MovementFlag.BLOCK_MOVEMENT_EAST);

        assertTrue(MovementFlag.containsAnyOf(east, westEast));
        assertTrue(MovementFlag.containsAnyOf(west, westEast));
        assertTrue(MovementFlag.containsAnyOf(westEast, east));
        assertTrue(MovementFlag.containsAnyOf(westEast, west));

        assertFalse(MovementFlag.containsAnyOf(west, east));
        assertFalse(MovementFlag.containsAnyOf(NO_MOVEMENT_FLAGS, east));
        assertFalse(MovementFlag.containsAnyOf(NO_MOVEMENT_FLAGS, west));
        assertFalse(MovementFlag.containsAnyOf(west, NO_MOVEMENT_FLAGS));
        assertFalse(MovementFlag.containsAnyOf(east, NO_MOVEMENT_FLAGS));
    }
}
