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
    LIST("list<?>"),
    TEXT("text"),
    ITERATOR("iterator"),
    NUMBER_LIST("list<number>"),
    BOOLEAN_LIST("list<boolean>"),
    VECTOR_LIST("list<vector>"),
    TEXT_LIST("list<text>");

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
            case RuntimeValue.TextValue ignored -> TEXT;
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
        if (!values.isEmpty() && values.stream().allMatch(RuntimeValue.NumberValue.class::isInstance)) {
            return NUMBER_LIST;
        }
        if (!values.isEmpty() && values.stream().allMatch(RuntimeValue.BooleanValue.class::isInstance)) {
            return BOOLEAN_LIST;
        }
        if (!values.isEmpty() && values.stream().allMatch(RuntimeValue.VectorValue.class::isInstance)) {
            return VECTOR_LIST;
        }
        if (!values.isEmpty() && values.stream().allMatch(RuntimeValue.TextValue.class::isInstance)) {
            return TEXT_LIST;
        }
        return LIST;
    }
}
