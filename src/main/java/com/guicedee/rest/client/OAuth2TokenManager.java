package com.guicedee.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicedee.rest.client.annotations.EndpointSecurity;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OAuth2 access tokens for REST client endpoints using the Client Credentials grant.
 * <p>
 * Tokens are cached per endpoint name and automatically refreshed when expired.
 * Thread-safe for concurrent access from multiple REST client instances.
 */
@Log4j2
public class OAuth2TokenManager
{
    private static final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder().build();

    /**
     * Buffer in seconds before actual expiry to trigger a refresh.
     */
    private static final long EXPIRY_BUFFER_SECONDS = 30;

    private OAuth2TokenManager() {}

    /**
     * Gets a valid access token for the given endpoint, fetching or refreshing as needed.
     *
     * @param endpointName the endpoint binding name
     * @param security     the security configuration with OAuth2 details
     * @return the access token, or {@code null} if token acquisition fails
     */
    public static String getToken(String endpointName, EndpointSecurity security)
    {
        CachedToken cached = tokenCache.get(endpointName);
        if (cached != null && !cached.isExpired())
        {
            return cached.accessToken;
        }

        synchronized (OAuth2TokenManager.class)
        {
            // Double-check after acquiring lock
            cached = tokenCache.get(endpointName);
            if (cached != null && !cached.isExpired())
            {
                return cached.accessToken;
            }

            try
            {
                CachedToken newToken = fetchToken(endpointName, security);
                if (newToken != null)
                {
                    tokenCache.put(endpointName, newToken);
                    return newToken.accessToken;
                }
            }
            catch (Exception e)
            {
                log.error("[{}] Failed to obtain OAuth2 token: {}", endpointName, e.getMessage(), e);
            }
        }

        return null;
    }

    /**
     * Clears all cached tokens. Useful for testing or forced re-authentication.
     */
    public static void clearCache()
    {
        tokenCache.clear();
    }

    /**
     * Clears the cached token for a specific endpoint.
     *
     * @param endpointName the endpoint binding name
     */
    public static void clearToken(String endpointName)
    {
        tokenCache.remove(endpointName);
    }

    private static CachedToken fetchToken(String endpointName, EndpointSecurity security) throws Exception
    {
        String tokenUrl = RestClient.resolveEnvPlaceholder(security.oauth2TokenUrl());
        String clientId = RestClient.resolveEnvPlaceholder(security.oauth2ClientId());
        String clientSecret = RestClient.resolveEnvPlaceholder(security.oauth2ClientSecret());
        String scopes = RestClient.resolveEnvPlaceholder(security.oauth2Scopes());
        String grantType = RestClient.resolveEnvPlaceholder(security.oauth2GrantType());

        if (tokenUrl == null || tokenUrl.isEmpty())
        {
            log.warn("[{}] OAuth2 token URL is not configured", endpointName);
            return null;
        }

        StringJoiner formBody = new StringJoiner("&");
        formBody.add("grant_type=" + encode(grantType));
        if (clientId != null && !clientId.isEmpty()) formBody.add("client_id=" + encode(clientId));
        if (clientSecret != null && !clientSecret.isEmpty()) formBody.add("client_secret=" + encode(clientSecret));
        if (scopes != null && !scopes.isEmpty()) formBody.add("scope=" + encode(scopes));

        log.debug("[{}] Requesting OAuth2 token from {} (grant_type={})", endpointName, tokenUrl, grantType);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300)
        {
            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.has("access_token") ? json.get("access_token").asText() : null;
            long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;

            if (accessToken == null || accessToken.isEmpty())
            {
                log.warn("[{}] OAuth2 response did not contain access_token", endpointName);
                return null;
            }

            log.debug("[{}] OAuth2 token obtained, expires in {} seconds", endpointName, expiresIn);
            return new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn));
        }
        else
        {
            log.error("[{}] OAuth2 token request failed with status {}: {}", endpointName, response.statusCode(), response.body());
            return null;
        }
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String accessToken, Instant expiresAt)
    {
        boolean isExpired()
        {
            return Instant.now().isAfter(expiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS));
        }
    }
}

