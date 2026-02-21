package com.guicedee.rest.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Connection tuning and behaviour options for a REST client endpoint.
 * <p>
 * All timing values are in milliseconds unless otherwise noted. Values of
 * {@code 0} indicate "use the Vert.x default".
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface EndpointOptions
{
    /**
     * TCP connect timeout in milliseconds.
     *
     * @return the connect timeout (0 = Vert.x default)
     */
    int connectTimeout() default 0;

    /**
     * Idle connection timeout in milliseconds.
     *
     * @return the idle timeout (0 = Vert.x default)
     */
    int idleTimeout() default 0;

    /**
     * Read timeout (response timeout) in milliseconds per request.
     *
     * @return the read timeout (0 = Vert.x default)
     */
    int readTimeout() default 0;

    /**
     * Whether to trust all server certificates (disables hostname verification).
     * <p>
     * <strong>Warning:</strong> only use for development and testing.
     *
     * @return true to trust all certificates
     */
    boolean trustAll() default false;

    /**
     * Whether to verify the server hostname against the certificate.
     *
     * @return true to verify hostname (default)
     */
    boolean verifyHost() default true;

    /**
     * Whether SSL/TLS should be enabled for this endpoint.
     * <p>
     * When {@code true} and the URL scheme is {@code https}, SSL is always enabled.
     * Set to {@code true} to force SSL even when the URL scheme is {@code http}.
     *
     * @return true to enable SSL
     */
    boolean ssl() default false;

    /**
     * Maximum connection pool size for this endpoint.
     *
     * @return the pool size (0 = Vert.x default)
     */
    int maxPoolSize() default 0;

    /**
     * Whether to keep connections alive between requests.
     *
     * @return true to enable keep-alive (default)
     */
    boolean keepAlive() default true;

    /**
     * Whether to enable automatic response body decompression (gzip/deflate).
     *
     * @return true to decompress responses
     */
    boolean decompression() default true;

    /**
     * Whether to follow HTTP redirects automatically.
     *
     * @return true to follow redirects (default)
     */
    boolean followRedirects() default true;

    /**
     * Maximum number of HTTP redirects to follow.
     *
     * @return the max redirects (0 = Vert.x default)
     */
    int maxRedirects() default 0;

    /**
     * Default content type to send with requests when no explicit content type is set.
     *
     * @return the content type (empty = none)
     */
    String defaultContentType() default "application/json";

    /**
     * Default Accept header value to send with requests.
     *
     * @return the accept header (empty = none)
     */
    String defaultAccept() default "application/json";

    /**
     * Number of retry attempts on connection failure (0 = no retries).
     *
     * @return the retry count
     */
    int retryAttempts() default 0;

    /**
     * Delay between retry attempts in milliseconds.
     *
     * @return the retry delay
     */
    long retryDelay() default 1000;
}

