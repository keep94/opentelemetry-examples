package com.vmware;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author Sumit Deo (deosu@vmware.com)
 */
public class App {

    private final static Logger logger = LoggerFactory.getLogger(App.class);
    public static final String INSTRUMENTATION_LIBRARY_NAME = "instrumentation-library-name";
    public static final String INSTRUMENTATION_VERSION = "1.0.0";
    static Tracer tracer;

    public static void main(String[] args) throws InterruptedException {

        /*tracer must be acquired, which is responsible for creating spans and interacting with the Context*/
        tracer = getTracer();

        //an automated way to propagate the parent span on the current thread
        for (int index = 0; index < 3; index++) {
            //create a span by specifying the name of the span. The start and end time of the span is automatically set by the OpenTelemetry SDK
            Span parentSpan =
                tracer.spanBuilder("parentSpan-" + index).setSpanKind(Span.Kind.SERVER).startSpan();
            logger.info("In parent method. TraceID : {}", parentSpan.getSpanContext().getTraceIdAsHexString());

            //put the span into the current Context
            try {
                parentSpan.makeCurrent();
                //annotate the span with attributes specific to the represented operation, to provide additional context
                parentSpan.setAttribute("parentIndex", index);
                childMethod(parentSpan);
            } catch (Throwable throwable) {
                parentSpan.setStatus(StatusCode.ERROR, "Exception message: " + throwable.getMessage());
                return;
            } finally {
                //closing the scope does not end the span, this has to be done manually
                parentSpan.end();
            }
        }

        //sleep for a bit to let everything settle
        Thread.sleep(2000);
    }

    private static void childMethod(Span parentSpan) {

        tracer = getTracer();

        //setParent(...) is not required, `Span.current()` is automatically added as the parent
        Span childSpan = tracer.spanBuilder("childSpan")
            .setParent(Context.current().with(parentSpan))
            .setSpanKind(Span.Kind.CLIENT)
            .startSpan();
        logger.info("In child method. TraceID : {}", childSpan.getSpanContext().getTraceIdAsHexString());

        AttributeKey<List<Long>> longAttr = AttributeKey.longArrayKey("long-arr");
        childSpan.setAttribute(longAttr, Arrays.asList(Integer.toUnsignedLong(1), Integer.toUnsignedLong(2)));
        AttributeKey<List<Boolean>> boolAttr = AttributeKey.booleanArrayKey("bool-arr");
        childSpan.setAttribute(boolAttr, Arrays.asList(true, false, true));

        //put the span into the current Context
        try (Scope scope = childSpan.makeCurrent()) {
            Thread.sleep(1000);
        } catch (Throwable throwable) {
            childSpan.setStatus(StatusCode.ERROR, "Something wrong with the child span");
        } finally {
            childSpan.setStatus(StatusCode.ERROR, "Something wrong with the child spanooo");
            childSpan.end();
        }
    }

    private static synchronized Tracer getTracer() {
        if (tracer == null) {

            //it is important to initialize your SDK as early as possible in your application's lifecycle
            OpenTelemetry openTelemetry = OTelConfig.initOpenTelemetry();

            //get a tracer
            tracer = openTelemetry.getTracer(INSTRUMENTATION_LIBRARY_NAME, INSTRUMENTATION_VERSION);
        }

        return tracer;
    }
}
