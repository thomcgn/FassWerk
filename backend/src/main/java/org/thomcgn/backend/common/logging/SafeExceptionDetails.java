package org.thomcgn.backend.common.logging;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Bounded code locations and cause types, never exception messages or payloads. */
public final class SafeExceptionDetails {
    private SafeExceptionDetails() {}

    public static String describe(Throwable failure) {
        StringBuilder result = new StringBuilder();
        // A depth limit also handles cyclic cause chains.
        for (int depth = 0; failure != null && depth < 5; depth++, failure = failure.getCause()) {
            if (depth > 0) result.append(" caused-by ");
            result.append(failure.getClass().getName());
            String locations = Arrays.stream(failure.getStackTrace())
                    .filter(frame -> frame.getClassName().startsWith("org.thomcgn.backend."))
                    .limit(8)
                    .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                    .collect(Collectors.joining(","));
            if (!locations.isEmpty()) result.append(" at ").append(locations);
        }
        return result.toString();
    }
}
