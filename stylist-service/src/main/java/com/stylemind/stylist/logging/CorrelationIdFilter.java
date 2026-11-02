package com.stylemind.stylist.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private final String serviceName;

    public CorrelationIdFilter(@Value("${spring.application.name:unknown}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incomingId = request.getHeader(HEADER_NAME);
        String correlationId = incomingId != null && SAFE_ID.matcher(incomingId).matches()
                ? incomingId
                : UUID.randomUUID().toString();
        long startNanos = System.nanoTime();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!"/actuator/health".equals(request.getRequestURI())) {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                log.info("Completed request service={} correlationId={} method={} path={} status={} durationMs={}",
                        serviceName, correlationId, request.getMethod(), request.getRequestURI(),
                        response.getStatus(), durationMs);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
