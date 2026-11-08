package com.stylemind.stylist.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies the P03-A request body limit and no-store policy without logging request content. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class SessionRequestPolicyFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 32 * 1024;
    private final ObjectMapper objectMapper;
    private final RequestMatcher sessionCollectionPath;
    private final RequestMatcher sessionHistoryPath;
    private final RequestMatcher requestSubmissionPath;
    private final RequestMatcher requestResourcePath;
    private final RequestMatcher generationOperationPath;
    private final RequestMatcher requestRecommendationsPath;
    private final RequestMatcher recommendationDetailPath;

    public SessionRequestPolicyFilter(ObjectMapper objectMapper,
                                      @Qualifier("requestMappingHandlerMapping")
                                      RequestMappingHandlerMapping handlerMapping) {
        this.objectMapper = objectMapper;
        var parser = handlerMapping.getPatternParser();
        if (parser == null) {
            throw new IllegalStateException("Stylist request policy requires the Spring MVC PathPatternParser");
        }
        var matcher = PathPatternRequestMatcher.withPathPatternParser(parser);
        this.sessionCollectionPath = matcher.matcher("/api/v1/stylist/sessions");
        this.sessionHistoryPath = matcher.matcher("/api/v1/stylist/sessions/{sessionId}/messages");
        this.requestSubmissionPath = matcher.matcher("/api/v1/stylist/sessions/{sessionId}/requests");
        this.requestResourcePath = matcher.matcher("/api/v1/stylist/requests/{requestId}");
        this.generationOperationPath = matcher.matcher("/api/v1/stylist/requests/{requestId}/generate");
        this.requestRecommendationsPath = matcher.matcher("/api/v1/stylist/requests/{requestId}/recommendations");
        this.recommendationDetailPath = matcher.matcher("/api/v1/stylist/recommendations/{recommendationId}");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean sessionCollection = sessionCollectionPath.matches(request);
        boolean sessionHistory = sessionHistoryPath.matches(request);
        boolean requestSubmission = requestSubmissionPath.matches(request);
        boolean requestResource = requestResourcePath.matches(request);
        boolean generationOperation = generationOperationPath.matches(request);
        boolean requestRecommendations = requestRecommendationsPath.matches(request);
        boolean recommendationDetail = recommendationDetailPath.matches(request);
        if (!sessionCollection && !sessionHistory && !requestSubmission && !requestResource
                && !generationOperation && !requestRecommendations && !recommendationDetail) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (hasUnknownQueryParameters(request, sessionHistory)) {
            writeProblem(request, response, HttpStatus.BAD_REQUEST,
                    "Bad Request", "Request is missing or malformed", "INVALID_REQUEST");
            return;
        }
        boolean bodylessGet = sessionHistory || requestResource || requestRecommendations || recommendationDetail;
        if ("GET".equals(request.getMethod()) && bodylessGet && hasBody(request)) {
            writeProblem(request, response, HttpStatus.BAD_REQUEST,
                    "Bad Request", "Request is missing or malformed", "INVALID_REQUEST");
            return;
        }

        if ("POST".equals(request.getMethod())
                && (sessionCollection || requestSubmission || generationOperation)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > MAX_BODY_BYTES) {
                writePayloadTooLarge(request, response);
                return;
            }
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                writePayloadTooLarge(request, response);
                return;
            }
            filterChain.doFilter(new CachedBodyRequest(request, body), response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean hasUnknownQueryParameters(HttpServletRequest request, boolean sessionHistory) {
        if (request.getQueryString() == null) return false;
        Set<String> allowed = sessionHistory ? Set.of("cursor", "limit") : Set.of();
        return request.getParameterMap().keySet().stream().anyMatch(name -> !allowed.contains(name));
    }

    private static boolean hasBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > 0) return true;
        if (request.getContentLengthLong() == 0) return false;
        return request.getInputStream().read() != -1;
    }

    private void writePayloadTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeProblem(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "Request body exceeds the allowed size", "PAYLOAD_TOO_LARGE");
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              String title, String detail, String code) throws IOException {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiProblemDetails.create(status, title, detail, code, request));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private ByteArrayInputStream source;
        private ServletInputStream servletInputStream;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            if (servletInputStream == null) {
                source = new ByteArrayInputStream(body);
                servletInputStream = new ServletInputStream() {
                    @Override public int read() { return source.read(); }
                    @Override public boolean isFinished() { return source.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener readListener) {
                        throw new IllegalStateException("Asynchronous body reads are not supported");
                    }
                };
            }
            return servletInputStream;
        }

        @Override
        public java.io.BufferedReader getReader() {
            String encoding = getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding();
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),
                    java.nio.charset.Charset.forName(encoding)));
        }

        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
