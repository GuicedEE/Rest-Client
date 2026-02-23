package com.guicedee.rest.client.implementations;

import com.google.inject.Provider;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.annotations.Endpoint;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;

/**
 * Guice {@link Provider} that constructs a {@link RestClient} from the endpoint
 * metadata discovered at scan time.
 * <p>
 * Each provider instance is bound to a single endpoint name and creates its
 * corresponding {@link WebClient} on first access.
 */
@Log4j2
public class RestClientProvider implements Provider<RestClient>
{
    private final String bindingName;

    /**
     * Creates a provider for the given endpoint binding name.
     *
     * @param bindingName the endpoint name used as the Guice {@code @Named} key
     */
    public RestClientProvider(String bindingName)
    {
        this.bindingName = bindingName;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RestClient get()
    {
        Endpoint endpoint = RestClientRegistry.getEndpointDefinitions().get(bindingName);
        String baseUrl = RestClientRegistry.getEndpointBaseUrls().get(bindingName);
        Type sendType = RestClientRegistry.getEndpointSendTypes().getOrDefault(bindingName, Object.class);
        Type receiveType = RestClientRegistry.getEndpointReceiveTypes().getOrDefault(bindingName, Object.class);
        Class<?> receiveClass = RestClientRegistry.getEndpointReceiveClasses().getOrDefault(bindingName, Object.class);

        if (endpoint == null || baseUrl == null)
        {
            throw new IllegalStateException(
                    "No @Endpoint registered for binding name '" + bindingName + "'. " +
                            "Ensure the field is annotated with @Endpoint and the module is on the classpath.");
        }

        Vertx vertx = VertXPreStartup.getVertx();
        WebClient webClient = WebClient.create(vertx);
        log.info("Created RestClient for endpoint: {} -> {}", bindingName, baseUrl);
        return new RestClient(webClient, endpoint, bindingName, baseUrl, sendType, receiveType, receiveClass);
    }
}

