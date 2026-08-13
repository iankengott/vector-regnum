package vectorregnum.core.vm2;

import java.util.List;

/** Compile-time stack types, including the list element shapes used by world operations. */
public enum StackType {
    NUMBER("number"),
    BOOLEAN("boolean"),
    POINT("point"),
    VECTOR("vector"),
    ENTITY("entity"),
    POINT_LIST("list<point>"),
    ENTITY_LIST("list<entity>"),
    LIST("list<?>");

    private final String displayName;

    StackType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static StackType of(RuntimeValue value) {
        return switch (value) {
            case RuntimeValue.NumberValue ignored -> NUMBER;
            case RuntimeValue.BooleanValue ignored -> BOOLEAN;
            case RuntimeValue.PointValue ignored -> POINT;
            case RuntimeValue.VectorValue ignored -> VECTOR;
            case RuntimeValue.EntityValue ignored -> ENTITY;
            case RuntimeValue.ListValue list -> listType(list.values());
        };
    }

    private static StackType listType(List<RuntimeValue> values) {
        if (!values.isEmpty() && values.stream().allMatch(RuntimeValue.PointValue.class::isInstance)) {
            return POINT_LIST;
        }
        if (!values.isEmpty() && values.stream().allMatch(RuntimeValue.EntityValue.class::isInstance)) {
            return ENTITY_LIST;
        }
        return LIST;
    }
}
