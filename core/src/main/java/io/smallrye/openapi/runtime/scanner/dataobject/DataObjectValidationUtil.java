package io.smallrye.openapi.runtime.scanner.dataobject;

public class DataObjectValidationUtil {
    public static void validateNotNull(Object input) {
        if (input == null) {
            throw DataObjectMessages.msg.notNull();
        }
    }

    public static void validateNotNull(Object input1, Object input2) {
        if (input1 == null) {
            throw DataObjectMessages.msg.notNull();
        }

        if (input2 == null) {
            throw DataObjectMessages.msg.notNull();
        }
    }

    public static void validateNotNull(Object... input) {
        for (Object t : input) {
            if (t == null)
                throw DataObjectMessages.msg.notNull();
        }
    }
}
