package com.advertisingmarketplace.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.ObjectProvider;

@UtilityClass
public class TraceIds {

    public static final String HEADER = "X-Trace-Id";

    public static Optional<String> current(ObjectProvider<Tracer> tracer) {
        Tracer available = tracer.getIfAvailable();
        Span span = available == null ? null : available.currentSpan();
        if (span == null) {
            return Optional.empty();
        }
        String traceId = span.context().traceId();
        return traceId == null || traceId.isBlank() ? Optional.empty() : Optional.of(traceId);
    }
}
