package com.guicedee.rest.client;

import lombok.Getter;

/**
 * Exception thrown when a REST client request results in a non-2xx HTTP response.
 * <p>
 * Carries the HTTP status code, status message, and response body so that
 * callers receiving a {@code Uni.failure} can inspect the details.
 */
@Getter
public class RestClientException extends RuntimeException
{
    private final int statusCode;
    private final String statusMessage;
    private final String responseBody;

    public RestClientException(int statusCode, String statusMessage, String responseBody)
    {
        super("HTTP " + statusCode + " " + statusMessage + (responseBody != null && !responseBody.isEmpty() ? ": " + responseBody : ""));
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.responseBody = responseBody;
    }

    /**
     * Returns {@code true} when the status code indicates a client error (4xx).
     */
    public boolean isClientError()
    {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Returns {@code true} when the status code indicates a server error (5xx).
     */
    public boolean isServerError()
    {
        return statusCode >= 500 && statusCode < 600;
    }
}

