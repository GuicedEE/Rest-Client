package com.guicedee.rest.client.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a REST client endpoint configuration on an injected {@link com.guicedee.rest.client.RestClient} field.
 * <p>
 * Use alongside {@code @Named("endpointName")} to identify the endpoint binding.
 * The annotation carries all information required to configure a Vert.x WebClient connection:
 * URL, HTTP method, protocol version, security, and connection tuning options.
 * <p>
 * Supports environment variable placeholders using {@code ${VAR_NAME}} syntax.
 *
 * <pre>{@code
 * @Endpoint(url = "https://api.example.com/users", method = "POST",
 *           security = @EndpointSecurity(value = SecurityType.Bearer, token = "${API_TOKEN}"),
 *           options  = @EndpointOptions(connectTimeout = 5000))
 * @Named("create-user")
 * private RestClient<CreateUserRequest, UserResponse> userClient;
 *
 * userClient.send(newUser).subscribe().with(user -> ..., err -> ...);
 * }</pre>
 */
@Retention(RUNTIME)
@Target({PACKAGE, METHOD, FIELD, PARAMETER, TYPE})
public @interface Endpoint
{
    /**
     * The base URL for the endpoint (e.g. {@code "https://api.example.com"}).
     * Supports environment variable placeholders: {@code "${API_BASE_URL}"}.
     */
    String url();

    /**
     * The HTTP method to use when calling {@code send()} or {@code publish()}.
     * Defaults to {@code GET}.
     */
    String method() default "GET";

    /**
     * The HTTP protocol version to use for this endpoint.
     */
    Protocol protocol() default Protocol.HTTP;

    /**
     * Security configuration for authenticating requests to this endpoint.
     */
    EndpointSecurity security() default @EndpointSecurity;

    /**
     * Tuning and connection options for this endpoint.
     */
    EndpointOptions options() default @EndpointOptions;

    /**
     * The HTTP protocol version for endpoint connections.
     */
    enum Protocol
    {
        HTTP,
        HTTP2
    }
}
