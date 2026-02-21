package com.guicedee.rest.client;

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
import java.util.Map;

/**
 * Injectable REST client wrapping a Vert.x {@link WebClient}, fully configured
 * from {@link Endpoint} annotation metadata (URL, HTTP method, security, options).
 * <p>
 * All configuration — URL, method, headers, authentication, timeouts — is driven
 * entirely by the annotation. Callers only need {@link #send()} or {@link #publish(Object)}.
 * <p>
 * Returns {@link Uni}{@code <Receive>} directly — 2xx yields the deserialized body,
 * non-2xx and transport errors surface as {@code Uni.failure(}{@link RestClientException}{@code )}.
 *
 * <pre>{@code
 * @Endpoint(url = "https://api.example.com/users", method = "POST",
 *           security = @EndpointSecurity(value = SecurityType.Bearer, token = "${API_TOKEN}"))
 * @Named("create-user")
 * private RestClient<CreateUserRequest, UserResponse> userClient;
 *
 * userClient.send(newUser)
 *           .subscribe().with(user -> log.info("Created: {}", user),
 *                             err  -> log.error("Failed", err));
 * }</pre>
 *
 * @param <Send>    the request body type
 * @param <Receive> the response body type
 */
@Log4j2
@Getter
public class RestClient<Send, Receive>
{
    private final WebClient webClient;
    private final Endpoint endpoint;
    private final String baseUrl;
    private final Type sendType;
    private final Type receiveType;
    private final Class<Receive> receiveClass;
    private final HttpMethod defaultMethod;

    @SuppressWarnings("unchecked")
    public RestClient(WebClient webClient, Endpoint endpoint, String baseUrl,
                      Type sendType, Type receiveType, Class<?> receiveClass)
    {
        this.webClient = webClient;
        this.endpoint = endpoint;
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
     * Sends a request with no body using the method and URL from the annotation.
     *
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send()
    {
        return doSend(null, null, null);
    }

    /**
     * Sends a request with a typed body using the method and URL from the annotation.
     *
     * @param body the request body
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send(Send body)
    {
        return doSend(body, null, null);
    }

    /**
     * Sends a request with a typed body and additional headers/query parameters.
     *
     * @param body        the request body (may be {@code null})
     * @param headers     additional headers (may be {@code null})
     * @param queryParams additional query parameters (may be {@code null})
     * @return a {@code Uni<Receive>} with the deserialized response body
     */
    public Uni<Receive> send(Send body, Map<String, String> headers, Map<String, String> queryParams)
    {
        return doSend(body, headers, queryParams);
    }

    /**
     * Publishes a body (fire-and-forget) using the method and URL from the annotation.
     *
     * @param body the request body
     * @return a {@code Uni<Void>} that completes when the request finishes
     */
    public Uni<Void> publish(Send body)
    {
        return doSend(body, null, null).replaceWithVoid();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal — single execution path
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Uni<Receive> doSend(Send body, Map<String, String> headers, Map<String, String> queryParams)
    {
        return Uni.createFrom().deferred(() -> {
            try
            {
                URI uri = URI.create(baseUrl);
                String host = uri.getHost();
                String path = uri.getRawPath() != null ? uri.getRawPath() : "/";

                // Relative path (no scheme/host) — default to localhost
                if (host == null || host.isEmpty())
                {
                    host = "localhost";
                    path = baseUrl.startsWith("/") ? baseUrl : "/" + baseUrl;
                    int qIdx = path.indexOf('?');
                    if (qIdx >= 0) path = path.substring(0, qIdx);
                }

                int port = uri.getPort();
                boolean isSsl = "https".equalsIgnoreCase(uri.getScheme()) || endpoint.options().ssl();
                if (port == -1)
                {
                    port = isSsl ? 443 : 80;
                }

                HttpRequest<Buffer> raw = webClient.request(defaultMethod, port, host, path);
                raw.ssl(isSsl);

                // Typed BodyCodec for response deserialization
                HttpRequest<Receive> request;
                if (receiveClass == String.class)
                {
                    request = (HttpRequest<Receive>) (HttpRequest<?>) raw.as(BodyCodec.string());
                }
                else if (receiveClass != null && receiveClass != Void.class && receiveClass != Object.class)
                {
                    request = raw.as(BodyCodec.json(receiveClass));
                }
                else
                {
                    request = (HttpRequest<Receive>) (HttpRequest<?>) raw.as(BodyCodec.buffer());
                }

                // Timeouts
                EndpointOptions opts = endpoint.options();
                if (opts.connectTimeout() > 0)  request.connectTimeout(opts.connectTimeout());
                if (opts.idleTimeout() > 0)     request.idleTimeout(opts.idleTimeout());
                if (opts.readTimeout() > 0)     request.timeout(opts.readTimeout());

                // Default headers
                if (!opts.defaultAccept().isEmpty())       request.putHeader("Accept", opts.defaultAccept());
                if (!opts.defaultContentType().isEmpty())   request.putHeader("Content-Type", opts.defaultContentType());

                // Follow redirects
                request.followRedirects(opts.followRedirects());

                // Authentication
                applyAuthentication(request);

                // URI query string
                if (uri.getQuery() != null && !uri.getQuery().isEmpty())
                {
                    for (String param : uri.getQuery().split("&"))
                    {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2) request.addQueryParam(kv[0], kv[1]);
                    }
                }

                // Additional query params / headers
                if (queryParams != null) queryParams.forEach(request::addQueryParam);
                if (headers != null)     headers.forEach(request::putHeader);

                // Dispatch
                Future<HttpResponse<Receive>> vertxFuture = body != null
                        ? request.sendJson(body)
                        : request.send();

                return Uni.createFrom().completionStage(vertxFuture.toCompletionStage())
                        .map(resp -> {
                            int status = resp.statusCode();
                            if (status >= 200 && status < 300)
                            {
                                return resp.body();
                            }
                            throw new RestClientException(status, resp.statusMessage(),
                                    resp.bodyAsBuffer() != null ? resp.bodyAsBuffer().toString() : null);
                        })
                        .onFailure().invoke(err -> {
                            if (err instanceof RestClientException rce)
                            {
                                log.warn("REST {} {} returned {}", defaultMethod, baseUrl, rce.getStatusCode());
                            }
                            else
                            {
                                log.error("REST {} {} failed: {}", defaultMethod, baseUrl, err.getMessage(), err);
                            }
                        });
            }
            catch (Exception e)
            {
                log.error("Error preparing REST request {} {}: {}", defaultMethod, baseUrl, e.getMessage(), e);
                return Uni.createFrom().failure(e);
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Authentication
    // ──────────────────────────────────────────────────────────────────────────

    private void applyAuthentication(HttpRequest<?> request)
    {
        EndpointSecurity security = endpoint.security();
        if (security == null) return;

        switch (security.value())
        {
            case Bearer, JWT ->
            {
                String token = resolveEnvPlaceholder(security.token());
                if (token != null && !token.isEmpty()) request.bearerTokenAuthentication(token);
            }
            case Basic ->
            {
                String username = resolveEnvPlaceholder(security.username());
                String password = resolveEnvPlaceholder(security.password());
                if (username != null && !username.isEmpty())
                    request.basicAuthentication(username, password != null ? password : "");
            }
            case ApiKey ->
            {
                String apiKey = resolveEnvPlaceholder(security.apiKey());
                if (apiKey != null && !apiKey.isEmpty()) request.putHeader(security.apiKeyHeader(), apiKey);
            }
            default -> { /* None */ }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────


    public static String resolveEnvPlaceholder(String value)
    {
        if (value == null || value.isEmpty()) return value;
        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment(value, value);
    }
}
