package com.guicedee.rest.client;

import com.guicedee.rest.client.annotations.Endpoint;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * SPI extension point for contributing additional {@link WebClientOptions}
 * configuration to REST client endpoints.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} and invoked
 * during {@link RestClient} construction. This allows adding custom SSL keystores,
 * proxy settings, or other Vert.x options not covered by the annotation model.
 *
 * <pre>{@code
 * public class MyRestClientConfigurator implements RestClientConfigurator {
 *     @Override
 *     public WebClientOptions configure(String endpointName, Endpoint endpoint, WebClientOptions options) {
 *         if ("secure-api".equals(endpointName)) {
 *             options.setKeyCertOptions(myKeyCert);
 *         }
 *         return options;
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface RestClientConfigurator
{
    /**
     * Applies additional configuration to the web client options for the given endpoint.
     *
     * @param endpointName the logical endpoint name
     * @param endpoint     the endpoint annotation metadata
     * @param options      the current web client options (mutable)
     * @return the configured options (may return the same instance or a new one)
     */
    WebClientOptions configure(String endpointName, Endpoint endpoint, WebClientOptions options);
}

