package com.guicedee.rest.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security configuration for a REST client endpoint.
 * <p>
 * Supports multiple authentication strategies. Credential values support
 * environment variable placeholders: {@code "${MY_SECRET}"}.
 *
 * <pre>{@code
 * @Endpoint(value = "https://api.example.com",
 *           security = @EndpointSecurity(value = SecurityType.Bearer,
 *                                        token = "${API_TOKEN}"))
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface EndpointSecurity
{
    /**
     * The authentication strategy to use.
     *
     * @return the security type
     */
    SecurityType value() default SecurityType.None;

    /**
     * Bearer or JWT token value. Supports environment variable placeholders.
     * Applied when {@link #value()} is {@link SecurityType#Bearer} or {@link SecurityType#JWT}.
     *
     * @return the token
     */
    String token() default "";

    /**
     * Username for Basic authentication. Supports environment variable placeholders.
     * Applied when {@link #value()} is {@link SecurityType#Basic}.
     *
     * @return the username
     */
    String username() default "";

    /**
     * Password for Basic authentication. Supports environment variable placeholders.
     * Applied when {@link #value()} is {@link SecurityType#Basic}.
     *
     * @return the password
     */
    String password() default "";

    /**
     * API key value. Supports environment variable placeholders.
     * Applied when {@link #value()} is {@link SecurityType#ApiKey}.
     *
     * @return the API key
     */
    String apiKey() default "";

    /**
     * Header name for the API key. Defaults to {@code X-API-Key}.
     * Applied when {@link #value()} is {@link SecurityType#ApiKey}.
     *
     * @return the header name
     */
    String apiKeyHeader() default "X-API-Key";

    // ── OAuth2 / OIDC (Client Credentials or Authorization Code) ──

    /**
     * OAuth2 token endpoint URL for obtaining access tokens.
     * Applied when {@link #value()} is {@link SecurityType#OAuth2}.
     *
     * @return the token URL
     */
    String oauth2TokenUrl() default "";

    /**
     * OAuth2 client ID.
     * Applied when {@link #value()} is {@link SecurityType#OAuth2}.
     *
     * @return the client ID
     */
    String oauth2ClientId() default "";

    /**
     * OAuth2 client secret.
     * Applied when {@link #value()} is {@link SecurityType#OAuth2}.
     *
     * @return the client secret
     */
    String oauth2ClientSecret() default "";

    /**
     * OAuth2 scopes (space-separated).
     * Applied when {@link #value()} is {@link SecurityType#OAuth2}.
     *
     * @return the scopes
     */
    String oauth2Scopes() default "";

    /**
     * OAuth2 grant type (e.g. "client_credentials", "authorization_code").
     * Applied when {@link #value()} is {@link SecurityType#OAuth2}.
     *
     * @return the grant type
     */
    String oauth2GrantType() default "client_credentials";

    // ── Mutual TLS (Client Certificate) ──

    /**
     * Path to the client certificate keystore (JKS or PKCS12).
     * Applied when {@link #value()} is {@link SecurityType#MutualTLS}.
     *
     * @return the keystore path
     */
    String clientCertPath() default "";

    /**
     * Password for the client certificate keystore.
     * Applied when {@link #value()} is {@link SecurityType#MutualTLS}.
     *
     * @return the keystore password
     */
    String clientCertPassword() default "";

    /**
     * Keystore type for the client certificate (JKS, PKCS12).
     * Applied when {@link #value()} is {@link SecurityType#MutualTLS}.
     *
     * @return the keystore type
     */
    String clientCertType() default "PKCS12";

    /**
     * Supported authentication strategies.
     */
    enum SecurityType
    {
        /**
         * No authentication.
         */
        None,
        /**
         * JWT token passed as {@code Authorization: Bearer <token>}.
         */
        JWT,
        /**
         * Bearer token passed as {@code Authorization: Bearer <token>}.
         */
        Bearer,
        /**
         * Basic authentication with username and password.
         */
        Basic,
        /**
         * API key passed via a configurable header.
         */
        ApiKey,
        /**
         * OAuth2 / OIDC — automatically obtains and refreshes access tokens
         * using the configured token endpoint and client credentials.
         */
        OAuth2,
        /**
         * Mutual TLS (mTLS) — presents a client certificate for authentication.
         */
        MutualTLS
    }
}
