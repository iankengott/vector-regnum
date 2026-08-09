package vectorregnum.core;

enum Opcode {
    SET_ORIGIN(5.0, 1),
    EXPAND_AREA(15.0, 3),
    SET_VECTOR(10.0, 2),
    APPLY_ELEMENT(20.0, 4),
    RESOLVE_SHAPE(25.0, 5),
    EXECUTE_EFFECT(5.0, 1),
    AMPLIFY(30.0, 4),
    FAULT(0.0, 0);

    final double baseManaCost;
    final int complexityWeight;

    Opcode(double baseManaCost, int complexityWeight) {
        this.baseManaCost = baseManaCost;
        this.complexityWeight = complexityWeight;
    }
}
