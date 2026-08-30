package vectorregnum.core.vm2;

/** Stable VM instruction names. Stack order is documented on {@link Instruction}. */
public enum Opcode {
    PUSH, POP, DUP,
    ADD, SUBTRACT, MULTIPLY, DIVIDE,
    EQUALS, LESS_THAN, GREATER_THAN, NOT, AND, OR,
    JUMP, JUMP_IF_FALSE, LOOP,
    DELAY, SET_DURATION,
    SELECT_RADIUS, RAYCAST_ENTITIES,
    IMPULSE, ACCELERATION, DAMPING, FOLLOW_PATH, MOVE_TOWARD, KEEP_DISTANCE,
    SEMANTIC,
    HALT,
    // Append-only: presentation codecs persist these ordinals.
    STORE_VARIABLE, LOAD_VARIABLE,
    ITERATOR_BEGIN, ITERATOR_NEXT,
    COLLISION,
    WATCH_VARIABLE, SIGNAL, OUTPUT,
    FORK, JOIN, CANCEL_BRANCH, BRANCH_END
}
