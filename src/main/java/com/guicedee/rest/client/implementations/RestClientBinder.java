package com.guicedee.rest.client.implementations;

import com.google.inject.*;
import com.google.inject.name.Names;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.annotations.Endpoint;
import com.guicedee.rest.client.annotations.EndpointOptions;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guice module that binds {@link RestClient} instances for each discovered
 * {@link Endpoint}-annotated field, keyed by {@code @Named("endpointName")}.
 * <p>
 * Binds using the full parameterized type (e.g. {@code RestClient<String, String>})
 * so Guice can match the generic field type at the injection point.
 * Each injection creates a new {@code RestClient}; the underlying {@link WebClient} is cached.
 */
@Log4j2
@SuppressWarnings({"unchecked", "rawtypes"})
public class RestClientBinder extends AbstractModule implements IGuiceModule<RestClientBinder>
{
    private static final Map<String, WebClient> webClientCache = new ConcurrentHashMap<>();

    @Override
    protected void configure()
    {
        try
        {
            RestClientRegistry.scanAndRegisterEndpoints();
        }
        catch (Throwable t)
        {
            log.warn("RestClientRegistry.scanAndRegisterEndpoints() failed: {}", t.getMessage());
        }

        for (var entry : RestClientRegistry.getEndpointDefinitions().entrySet())
        {
            String bindingName = entry.getKey();
            Endpoint wrappedEndpoint = entry.getValue();

            String resolvedUrl = RestClientRegistry.getEndpointBaseUrls().get(bindingName);
            Type sendType = RestClientRegistry.getEndpointSendTypes().getOrDefault(bindingName, Object.class);
            Type receiveType = RestClientRegistry.getEndpointReceiveTypes().getOrDefault(bindingName, Object.class);
            Class<?> receiveClass = RestClientRegistry.getEndpointReceiveClasses().getOrDefault(bindingName, Object.class);
            Type fieldType = RestClientRegistry.getEndpointFieldTypes().get(bindingName);

            // Build the Key with the full parameterized type + @Named qualifier
            Key<RestClient> key;
            if (fieldType != null && fieldType != RestClient.class)
            {
                TypeLiteral<RestClient> typeLiteral = (TypeLiteral<RestClient>) TypeLiteral.get(fieldType);
                key = Key.get(typeLiteral, Names.named(bindingName));
            }
            else
            {
                key = Key.get(RestClient.class, Names.named(bindingName));
            }

            bind(key).toProvider(() -> {
                // Use the pre-resolved URL from the registry (includes package-level base URL
                // concatenation and environment variable resolution)
                String lazyResolvedUrl = RestClientRegistry.getEndpointBaseUrls().get(bindingName);
                WebClient webClient = webClientCache.computeIfAbsent(bindingName, k -> {
                    WebClientOptions options = buildWebClientOptions(wrappedEndpoint);
                    return WebClient.create(VertXPreStartup.getVertx(), options);
                });
                return new RestClient(webClient, wrappedEndpoint, bindingName, lazyResolvedUrl, sendType, receiveType, receiveClass);
            });

            log.debug("Bound RestClient @Named(\"{}\") -> {}", bindingName, resolvedUrl);
        }
    }

    /**
     * Builds Vert.x {@link WebClientOptions} from the {@link Endpoint} annotation.
     */
    static WebClientOptions buildWebClientOptions(Endpoint endpoint)
    {
        EndpointOptions opts = endpoint.options();
        WebClientOptions options = new WebClientOptions();

        if (endpoint.protocol() == Endpoint.Protocol.HTTP2)
        {
            options.setProtocolVersion(HttpVersion.HTTP_2);
            options.setHttp2ClearTextUpgrade(true);
        }

        if (opts.connectTimeout() > 0) options.setConnectTimeout(opts.connectTimeout());
        if (opts.idleTimeout() > 0)    options.setIdleTimeout(opts.idleTimeout());

        String resolvedUrl = RestClientRegistry.resolveUrl(endpoint.url());
        boolean needsSsl = opts.ssl() || (resolvedUrl != null && resolvedUrl.startsWith("https"));
        options.setSsl(needsSsl);
        options.setTrustAll(opts.trustAll());
        options.setVerifyHost(opts.verifyHost());

        if (opts.maxPoolSize() > 0) options.setShared(true);

        options.setKeepAlive(opts.keepAlive());
        options.setDecompressionSupported(opts.decompression());
        options.setFollowRedirects(opts.followRedirects());

        if (opts.maxRedirects() > 0) options.setMaxRedirects(opts.maxRedirects());

        options.setUserAgent("GuicedEE-RestClient/2.0");

        return options;
    }
}
