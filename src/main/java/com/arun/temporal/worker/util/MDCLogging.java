package com.arun.temporal.worker.util;

import io.opentelemetry.api.trace.Span;
import org.slf4j.MDC;

import java.util.Map;

public class MDCLogging {
    public static void setValueInMDCContext(String workspaceId, String email, String requestId, Span span, String isTrialUser) {
        MDC.put("workspace_id", workspaceId);
        MDC.put("user", email);
        MDC.put("dd.trace_id", getTraceId(span));
        MDC.put("dd.span_id", getSpanId(span));
        MDC.put("req_id", requestId);
        MDC.put("is_trial_user", isTrialUser);
    }

    private static String getTraceId(Span span) {
        return convertToUnsignedString(span.getSpanContext().getTraceId());
    }

    private static String getSpanId(Span span) {
        return convertToUnsignedString(span.getSpanContext().getTraceId());
    }

    private static String convertToUnsignedString(String id) {
        String traceIdHexString = id.substring(id.length() - 16);
        long traceIdLong = Long.parseUnsignedLong(traceIdHexString, 16);
        return Long.toUnsignedString(traceIdLong);
    }

    public static void clearMDC() {
        MDC.clear();
    }

    public static void setMDCContext(Map<String, String> contextMap) {
        MDC.clear();
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }
}

