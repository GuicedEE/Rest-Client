package com.guicedee.rest.client;

import lombok.Getter;

/**
 * Exception thrown when a REST client call fails for any reason — non-2xx HTTP response,
 * deserialization error, transport failure, or request preparation error.
 * <p>
 * Always carries as much detail as available: HTTP status code, status message,
 * response body, and the underlying cause.
 */
@Getter
public class RestClientException extends RuntimeException
{
    private final int statusCode;
    private final String statusMessage;
    private final String responseBody;

    public RestClientException(int statusCode, String statusMessage, String responseBody)
    {
        super(buildMessage(statusCode, statusMessage, responseBody));
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.responseBody = responseBody;
    }

    public RestClientException(int statusCode, String statusMessage, String responseBody, Throwable cause)
    {
        super(buildMessage(statusCode, statusMessage, responseBody), cause);
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.responseBody = responseBody;
    }

    public RestClientException(String message, Throwable cause)
    {
        super(message, cause);
        this.statusCode = 0;
        this.statusMessage = message;
        this.responseBody = null;
    }

    private static String buildMessage(int statusCode, String statusMessage, String responseBody)
    {
        StringBuilder sb = new StringBuilder();
        if (statusCode > 0) sb.append("HTTP ").append(statusCode).append(" ");
        if (statusMessage != null) sb.append(statusMessage);
        if (responseBody != null && !responseBody.isEmpty()) sb.append(": ").append(responseBody);
        return sb.toString().trim();
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

    /**
     * Returns {@code true} when this represents a transport/connection error (no HTTP status).
     */
    public boolean isTransportError()
    {
        return statusCode == 0;
    }
}
