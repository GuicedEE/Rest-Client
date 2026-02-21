package com.guicedee.rest.client.implementations;

import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Pre-startup hook that triggers {@link RestClientRegistry} scanning during the
 * Guice lifecycle, before the injector is built.
 * <p>
 * Follows the same lifecycle pattern as {@code VertXPreStartup} and {@code RabbitMQPreStartup}.
 */
@Log4j2
public class RestClientPreStartup implements IGuicePreStartup<RestClientPreStartup>
{

    @Override
    public List<Future<Boolean>> onStartup()
    {
        log.info("RestClient pre-startup: scanning for @Endpoint annotations");
        RestClientRegistry.scanAndRegisterEndpoints();
        return List.of(Future.succeededFuture(true));
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 100;
    }
}

