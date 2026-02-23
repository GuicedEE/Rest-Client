package com.guicedee.rest.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.rest.client.annotations.Endpoint;
import com.guicedee.rest.client.annotations.EndpointOptions;
import com.guicedee.rest.client.annotations.EndpointSecurity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.guicedee.client.implementations.ObjectBinderKeys.DefaultObjectMapper;

/**
 * Injectable REST client wrapping a Vert.x {@link WebClient}, fully configured
 * from {@link Endpoint} annotation metadata (URL, HTTP method, security, options).
 * <p>
 * All configuration — URL, method, headers, authentication, timeouts — is driven
 * entirely by the annotation. Callers only need {@link #send()} or {@link #publish(Object)}.
 * <p>
 * Returns {@link Uni}{@code <Receive>} directly — 2xx yields the deserialized body,
 * non-2xx and transport errors surface as {@code Uni.failure(}{@link RestClientException}{@code )}.
 * <p>
 * Supports path parameters using {@code {paramName}} placeholders in the endpoint URL.
 * Use {@link #pathParam(String, String)} for a fluent builder, or pass a {@code Map} to the
 * {@code send}/{@code publish} overloads.
 *
 * <pre>{@code
 * // Simple endpoint (no path params)
 * @Endpoint(url = "https://api.example.com/users", method = "POST",
 *           security = @EndpointSecurity(value = SecurityType.Bearer, token = "${API_TOKEN}"))
 * @Named("create-user")
 * private RestClient<CreateUserRequest, UserResponse> createClient;
 *
 * createClient.send(newUser)
 *             .subscribe().with(user -> log.info("Created: {}", user),
 *                               err  -> log.error("Failed", err));
 *
 * // Endpoint with path parameters
 * @Endpoint(url = "https://api.example.com/users/{userId}/orders/{orderId}", method = "GET")
 * @Named("get-order")
 * private RestClient<Void, OrderResponse> orderClient;
 *
 * orderClient.pathParam("userId", "123")
 *            .pathParam("orderId", "456")
 *            .send();
 * }</pre>
 *
 * @param <Send>    the request body type
 * @param <Receive> the response body type
 */
@Log4j2
@Getter
public class RestClient<Send, Receive> {
    private final WebClient webClient;
    private final Endpoint endpoint;
    private final String endpointName;
    private final String baseUrl;
    private final Type sendType;
    private final Type receiveType;
    private final Class<Receive> receiveClass;
    private final HttpMethod defaultMethod;

    @SuppressWarnings("unchecked")
    public RestClient(WebClient webClient, Endpoint endpoint, String endpointName, String baseUrl,
                      Type sendType, Type receiveType, Class<?> receiveClass) {
        this.webClient = webClient;
        this.endpoint = endpoint;
        this.endpointName = endpointName;
        this.baseUrl = baseUrl;
        this.sendType = sendType;
        this.receiveType = receiveType;
        this.receiveClass = receiveClass != null ? (Class<Receive>) receiveClass : (Class<Receive>) Object.class;
        this.defaultMethod = HttpMethod.valueOf(endpoint.method());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Begins building a request with path parameters.
     * <p>
     * Path parameters are placeholders in the endpoint URL enclosed in braces,
     * e.g. {@code {userId}}. Each call to {@code pathParam} replaces the
     * corresponding placeholder.
     *
     * <pre>{@code
     * // @Endpoint(url = "https://api.example.com/users/{userId}", method = "GET")
     * userClient.pathParam("userId", "42").send();
     * }</pre>
     *
     * @param name  the path parameter name (without braces)
     * @param value the value to substitute
     * @return a {@link RequestBuilder} for chaining further parameters and sending
     */
    public RequestBuilder pathParam(String name, String value) {
        return new RequestBuilder().pathParam(name, value);
    }

    /**
     * Sends a request with no body using the method and URL from the annotation.
     *
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send() {
        return doSend(null, null, null, null);
    }

    /**
     * Sends a request with a typed body using the method and URL from the annotation.
     *
     * @param body the request body
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send(Send body) {
        return doSend(body, null, null, null);
    }

    /**
     * Sends a request with a typed body and additional headers/query parameters.
     *
     * @param body        the request body (may be {@code null})
     * @param headers     additional headers (may be {@code null})
     * @param queryParams additional query parameters (may be {@code null})
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send(Send body, Map<String, String> headers, Map<String, String> queryParams) {
        return doSend(body, headers, queryParams, null);
    }

    /**
     * Sends a request with path parameters substituted into the endpoint URL.
     *
     * @param body       the request body (may be {@code null})
     * @param pathParams path parameter values keyed by placeholder name (without braces)
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send(Send body, Map<String, String> pathParams, Map<String, String> headers, Map<String, String> queryParams) {
        return doSend(body, headers, queryParams, pathParams);
    }

    /**
     * Publishes a body (fire-and-forget) using the method and URL from the annotation.
     *
     * @param body the request body
     * @return a {@code Uni<Void>} that completes when the request finishes
     */
    public Uni<Void> publish(Send body) {
        return doSend(body, null, null, null).replaceWithVoid();
    }

    /**
     * Fluent request builder for setting path parameters, headers, and query
     * parameters before sending.
     * <p>
     * Obtain an instance via {@link RestClient#pathParam(String, String)}.
     */
    public class RequestBuilder {
        private final Map<String, String> pathParams = new LinkedHashMap<>();
        private Map<String, String> headers;
        private Map<String, String> queryParams;

        /**
         * Sets a path parameter value.
         *
         * @param name  the placeholder name (without braces)
         * @param value the substitution value
         * @return this builder
         */
        public RequestBuilder pathParam(String name, String value) {
            pathParams.put(name, value);
            return this;
        }

        /**
         * Sets additional headers for this request.
         *
         * @param headers header name-value pairs
         * @return this builder
         */
        public RequestBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * Sets additional query parameters for this request.
         *
         * @param queryParams query parameter name-value pairs
         * @return this builder
         */
        public RequestBuilder queryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        /**
         * Sends the request with no body.
         *
         * @return a {@code Uni<Receive>} with the deserialized response body
         */
        public Uni<Receive> send() {
            return doSend(null, headers, queryParams, pathParams);
        }

        /**
         * Sends the request with a typed body.
         *
         * @param body the request body
         * @return a {@code Uni<Receive>} with the deserialized response body
         */
        public Uni<Receive> send(Send body) {
            return doSend(body, headers, queryParams, pathParams);
        }

        /**
         * Publishes a body (fire-and-forget).
         *
         * @param body the request body
         * @return a {@code Uni<Void>} that completes when the request finishes
         */
        public Uni<Void> publish(Send body) {
            return doSend(body, headers, queryParams, pathParams).replaceWithVoid();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal — single execution path
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Uni<Receive> doSend(Send body, Map<String, String> headers, Map<String, String> queryParams,
                                Map<String, String> pathParams) {
        return Uni.createFrom().deferred(() -> {
            String resolvedUrl = resolvePathParams(baseUrl, pathParams);
            try {
                URI uri = URI.create(resolvedUrl);
                String host = uri.getHost();
                String path = uri.getRawPath() != null ? uri.getRawPath() : "/";

                // Relative path (no scheme/host) — default to localhost
                if (host == null || host.isEmpty()) {
                    host = "localhost";
                    path = resolvedUrl.startsWith("/") ? resolvedUrl : "/" + resolvedUrl;
                    int qIdx = path.indexOf('?');
                    if (qIdx >= 0) path = path.substring(0, qIdx);
                }

                int port = uri.getPort();
                boolean isSsl = "https".equalsIgnoreCase(uri.getScheme()) || endpoint.options().ssl();
                if (port == -1) {
                    port = isSsl ? 443 : 80;
                }

                HttpRequest<Buffer> raw = webClient.request(defaultMethod, port, host, path);
                raw.ssl(isSsl);

                ObjectMapper objectMapper = IGuiceContext.get(DefaultObjectMapper);

                // Always receive as buffer — we deserialize with DefaultObjectMapper in the response chain
                HttpRequest<Buffer> request = raw.as(BodyCodec.buffer());

                // Timeouts
                EndpointOptions opts = endpoint.options();
                if (opts.connectTimeout() > 0) request.connectTimeout(opts.connectTimeout());
                if (opts.idleTimeout() > 0) request.idleTimeout(opts.idleTimeout());
                if (opts.readTimeout() > 0) request.timeout(opts.readTimeout());

                // Default headers
                if (!opts.defaultAccept().isEmpty()) request.putHeader("Accept", opts.defaultAccept());
                if (!opts.defaultContentType().isEmpty()) request.putHeader("Content-Type", opts.defaultContentType());

                // Follow redirects
                request.followRedirects(opts.followRedirects());

                // Authentication
                applyAuthentication(request);

                // URI query string
                if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
                    for (String param : uri.getQuery().split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2) request.addQueryParam(kv[0], kv[1]);
                    }
                }

                // Additional query params / headers
                if (queryParams != null) queryParams.forEach(request::addQueryParam);
                if (headers != null) headers.forEach(request::putHeader);

                // Dispatch — serialize body with DefaultObjectMapper
                Future<HttpResponse<Buffer>> vertxFuture;
                if (body != null) {
                    Buffer buffer;
                    if (body instanceof byte[] rawBytes) {
                        // Send raw bytes directly without JSON encoding
                        buffer = Buffer.buffer(rawBytes);
                        if (!opts.defaultContentType().isEmpty()) {
                            request.putHeader("Content-Type", opts.defaultContentType());
                        } else {
                            request.putHeader("Content-Type", "application/octet-stream");
                        }
                    } else {
                        byte[] jsonBytes = objectMapper.writeValueAsBytes(body);
                        buffer = Buffer.buffer(jsonBytes);
                        if (!opts.defaultContentType().isEmpty()) {
                            request.putHeader("Content-Type", opts.defaultContentType());
                        } else {
                            request.putHeader("Content-Type", "application/json");
                        }
                    }
                    vertxFuture = request.sendBuffer(buffer);
                } else {
                    vertxFuture = request.send();
                }

                return Uni.createFrom().completionStage(vertxFuture.toCompletionStage())
                        .map(resp -> {
                            int status = resp.statusCode();
                            if (status >= 200 && status < 300) {
                                Buffer responseBuffer = resp.body();
                                if (responseBuffer == null || responseBuffer.length() == 0) {
                                    return null;
                                }
                                try {
                                    if (receiveClass == String.class) {
                                        return (Receive) responseBuffer.toString();
                                    } else if (receiveClass == byte[].class) {
                                        return (Receive) responseBuffer.getBytes();
                                    } else if (receiveClass != null && receiveClass != Void.class && receiveClass != Object.class) {
                                        // Use full generic type so collections/arrays honour element types
                                        JavaType javaType = objectMapper.getTypeFactory().constructType(receiveType);
                                        return objectMapper.readValue(responseBuffer.getBytes(), javaType);
                                    } else {
                                        return (Receive) responseBuffer;
                                    }
                                } catch (Exception e) {
                                    throw new RestClientException(status, "Deserialization failed for " + receiveClass.getSimpleName(),
                                            responseBuffer.toString(), e);
                                }
                            }
                            throw new RestClientException(status, resp.statusMessage(),
                                    resp.body() != null ? resp.body().toString() : null);
                        })
                        .onFailure().transform(err -> {
                            if (err instanceof RestClientException rce) {
                                log.warn("[{}] REST {} {} returned {}", endpointName, defaultMethod, resolvedUrl, rce.getStatusCode());
                                return rce;
                            }
                            log.error("[{}] REST {} {} failed: {}", endpointName, defaultMethod, resolvedUrl, err.getMessage(), err);
                            return new RestClientException("[" + endpointName + "] REST " + defaultMethod + " " + resolvedUrl + " failed: " + err.getMessage(), err);
                        });
            } catch (Exception e) {
                log.error("[{}] Error preparing REST request {} {}: {}", endpointName, defaultMethod, resolvedUrl, e.getMessage(), e);
                return Uni.createFrom().failure(
                        new RestClientException("[" + endpointName + "] Error preparing REST request " + defaultMethod + " " + resolvedUrl + ": " + e.getMessage(), e));
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Authentication
    // ──────────────────────────────────────────────────────────────────────────

    private void applyAuthentication(HttpRequest<?> request) {
        EndpointSecurity security = endpoint.security();
        if (security == null) return;

        switch (security.value()) {
            case Bearer, JWT -> {
                String token = resolveEnvPlaceholder(security.token());
                if (token != null && !token.isEmpty()) request.bearerTokenAuthentication(token);
            }
            case Basic -> {
                String username = resolveEnvPlaceholder(security.username());
                String password = resolveEnvPlaceholder(security.password());
                if (username != null && !username.isEmpty())
                    request.basicAuthentication(username, password != null ? password : "");
            }
            case ApiKey -> {
                String apiKey = resolveEnvPlaceholder(security.apiKey());
                if (apiKey != null && !apiKey.isEmpty()) request.putHeader(security.apiKeyHeader(), apiKey);
            }
            default -> { /* None */ }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Replaces {@code {paramName}} placeholders in the URL with URL-encoded values.
     * <p>
     * Each value is percent-encoded (RFC 3986) so that characters such as spaces,
     * slashes, and other reserved characters are safe for use in URL path segments.
     *
     * @param url        the URL template containing placeholders
     * @param pathParams the parameter values (may be {@code null} or empty)
     * @return the URL with all placeholders resolved and values encoded
     * @throws IllegalArgumentException if any placeholder has no corresponding value
     */
    static String resolvePathParams(String url, Map<String, String> pathParams) {
        if (url == null || !url.contains("{")) return url;
        if (pathParams == null || pathParams.isEmpty()) return url;

        String resolved = url;
        for (var entry : pathParams.entrySet()) {
            String encoded = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            resolved = resolved.replace("{" + entry.getKey() + "}", encoded);
        }

        // Check for unresolved placeholders
        int openBrace = resolved.indexOf('{');
        if (openBrace >= 0) {
            int closeBrace = resolved.indexOf('}', openBrace);
            if (closeBrace > openBrace) {
                String missing = resolved.substring(openBrace + 1, closeBrace);
                throw new IllegalArgumentException(
                        "Unresolved path parameter '{" + missing + "}' in URL: " + url);
            }
        }

        return resolved;
    }

    public static String resolveEnvPlaceholder(String value) {
        if (value == null || value.isEmpty()) return value;
        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment(value, value);
    }
}
