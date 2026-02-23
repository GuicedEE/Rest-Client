package com.guicedee.rest.client.implementations;

import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.annotations.Endpoint;
import com.guicedee.rest.client.annotations.EndpointOptions;
import com.guicedee.rest.client.annotations.EndpointSecurity;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry that discovers {@link Endpoint}-annotated fields and maintains
 * the endpoint metadata required for {@link RestClient} creation.
 * <p>
 * Binding names are resolved from the {@code @Named} annotation on the field.
 */
@Log4j2
public class RestClientRegistry
{
    @Getter
    private static final Map<String, Endpoint> endpointDefinitions = new ConcurrentHashMap<>();

    @Getter
    private static final Map<String, Type> endpointSendTypes = new ConcurrentHashMap<>();

    @Getter
    private static final Map<String, Type> endpointReceiveTypes = new ConcurrentHashMap<>();

    @Getter
    private static final Map<String, Class<?>> endpointReceiveClasses = new ConcurrentHashMap<>();

    @Getter
    private static final Map<String, String> endpointBaseUrls = new ConcurrentHashMap<>();

    /**
     * Full generic field type per endpoint (e.g. {@code RestClient<String, String>}) for TypeLiteral binding.
     */
    @Getter
    private static final Map<String, Type> endpointFieldTypes = new ConcurrentHashMap<>();

    private static volatile boolean scanned = false;

    private RestClientRegistry() {}

    public static synchronized void scanAndRegisterEndpoints()
    {
        if (scanned) return;
        scanned = true;

        log.info("Scanning for @Endpoint-annotated RestClient fields");

        var classesWithFieldAnnotation = IGuiceContext.instance()
                .getScanResult()
                .getClassesWithFieldAnnotation(Endpoint.class);

        int count = 0;
        for (var classInfo : classesWithFieldAnnotation)
        {
            try
            {
                Class<?> clazz = classInfo.loadClass();
                for (Field field : clazz.getDeclaredFields())
                {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                    if (!RestClient.class.isAssignableFrom(field.getType())) continue;

                    Endpoint endpointAnnotation = field.getAnnotation(Endpoint.class);
                    if (endpointAnnotation == null) continue;

                    String bindingName = resolveBindingName(field);
                    if (endpointDefinitions.containsKey(bindingName))
                    {
                        log.debug("Endpoint '{}' already registered, skipping duplicate on {}.{}",
                                bindingName, clazz.getName(), field.getName());
                        continue;
                    }

                    Endpoint wrapped = wrapEndpoint(endpointAnnotation, bindingName);
                    String resolvedUrl = resolveUrl(wrapped.url());

                    Type[] typeArgs = extractTypeArguments(field);
                    Type sendType = typeArgs[0];
                    Type receiveType = typeArgs[1];
                    Class<?> receiveClass = toRawClass(receiveType);

                    endpointDefinitions.put(bindingName, wrapped);
                    endpointBaseUrls.put(bindingName, resolvedUrl);
                    endpointFieldTypes.put(bindingName, field.getGenericType());
                    endpointSendTypes.put(bindingName, sendType);
                    endpointReceiveTypes.put(bindingName, receiveType);
                    endpointReceiveClasses.put(bindingName, receiveClass);

                    count++;
                    log.debug("Registered REST endpoint: '{}' -> {} [Send={}, Receive={}] (field: {}.{})",
                            bindingName, resolvedUrl,
                            sendType.getTypeName(), receiveType.getTypeName(),
                            clazz.getSimpleName(), field.getName());
                }
            }
            catch (Exception e)
            {
                log.error("Error scanning class {} for @Endpoint fields", classInfo.getName(), e);
            }
        }

        log.info("Registered {} REST client endpoint(s)", count);
    }

    public static synchronized void reset()
    {
        endpointDefinitions.clear();
        endpointSendTypes.clear();
        endpointReceiveTypes.clear();
        endpointReceiveClasses.clear();
        endpointBaseUrls.clear();
        endpointFieldTypes.clear();
        scanned = false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Annotation wrapping (environment variable resolution)
    // ──────────────────────────────────────────────────────────────────────────

    private static Endpoint wrapEndpoint(Endpoint source, String bindingName)
    {
        return new Endpoint()
        {
            @Override public Class<? extends Annotation> annotationType() { return Endpoint.class; }
            @Override public String url() { return resolveEnv("REST_CLIENT_URL_" + bindingName, source.url()); }
            @Override public String method() { return resolveEnv("REST_CLIENT_METHOD_" + bindingName, source.method()); }
            @Override public Protocol protocol()
            {
                String proto = resolveEnv("REST_CLIENT_PROTOCOL_" + bindingName, source.protocol().name());
                try { return Protocol.valueOf(proto); }
                catch (IllegalArgumentException e) { return source.protocol(); }
            }
            @Override public EndpointSecurity security() { return wrapSecurity(source.security(), bindingName); }
            @Override public EndpointOptions options() { return wrapOptions(source.options(), bindingName); }
        };
    }

    private static EndpointSecurity wrapSecurity(EndpointSecurity source, String bindingName)
    {
        return new EndpointSecurity()
        {
            @Override public Class<? extends Annotation> annotationType() { return EndpointSecurity.class; }
            @Override public SecurityType value() { return source.value(); }
            @Override public String token() { return resolveEnv("REST_CLIENT_TOKEN_" + bindingName, source.token()); }
            @Override public String username() { return resolveEnv("REST_CLIENT_USERNAME_" + bindingName, source.username()); }
            @Override public String password() { return resolveEnv("REST_CLIENT_PASSWORD_" + bindingName, source.password()); }
            @Override public String apiKey() { return resolveEnv("REST_CLIENT_API_KEY_" + bindingName, source.apiKey()); }
            @Override public String apiKeyHeader() { return resolveEnv("REST_CLIENT_API_KEY_HEADER_" + bindingName, source.apiKeyHeader()); }
        };
    }

    private static EndpointOptions wrapOptions(EndpointOptions source, String bindingName)
    {
        return new EndpointOptions()
        {
            @Override public Class<? extends Annotation> annotationType() { return EndpointOptions.class; }
            @Override public int connectTimeout() { return Integer.parseInt(resolveEnv("REST_CLIENT_CONNECT_TIMEOUT_" + bindingName, String.valueOf(source.connectTimeout()))); }
            @Override public int idleTimeout() { return Integer.parseInt(resolveEnv("REST_CLIENT_IDLE_TIMEOUT_" + bindingName, String.valueOf(source.idleTimeout()))); }
            @Override public int readTimeout() { return Integer.parseInt(resolveEnv("REST_CLIENT_READ_TIMEOUT_" + bindingName, String.valueOf(source.readTimeout()))); }
            @Override public boolean trustAll() { return Boolean.parseBoolean(resolveEnv("REST_CLIENT_TRUST_ALL_" + bindingName, String.valueOf(source.trustAll()))); }
            @Override public boolean verifyHost() { return Boolean.parseBoolean(resolveEnv("REST_CLIENT_VERIFY_HOST_" + bindingName, String.valueOf(source.verifyHost()))); }
            @Override public boolean ssl() { return Boolean.parseBoolean(resolveEnv("REST_CLIENT_SSL_" + bindingName, String.valueOf(source.ssl()))); }
            @Override public int maxPoolSize() { return Integer.parseInt(resolveEnv("REST_CLIENT_MAX_POOL_SIZE_" + bindingName, String.valueOf(source.maxPoolSize()))); }
            @Override public boolean keepAlive() { return Boolean.parseBoolean(resolveEnv("REST_CLIENT_KEEP_ALIVE_" + bindingName, String.valueOf(source.keepAlive()))); }
            @Override public boolean decompression() { return Boolean.parseBoolean(resolveEnv("REST_CLIENT_DECOMPRESSION_" + bindingName, String.valueOf(source.decompression()))); }
            @Override public boolean followRedirects() { return Boolean.parseBoolean(resolveEnv("REST_CLIENT_FOLLOW_REDIRECTS_" + bindingName, String.valueOf(source.followRedirects()))); }
            @Override public int maxRedirects() { return Integer.parseInt(resolveEnv("REST_CLIENT_MAX_REDIRECTS_" + bindingName, String.valueOf(source.maxRedirects()))); }
            @Override public String defaultContentType() { return resolveEnv("REST_CLIENT_CONTENT_TYPE_" + bindingName, source.defaultContentType()); }
            @Override public String defaultAccept() { return resolveEnv("REST_CLIENT_ACCEPT_" + bindingName, source.defaultAccept()); }
            @Override public int retryAttempts() { return Integer.parseInt(resolveEnv("REST_CLIENT_RETRY_ATTEMPTS_" + bindingName, String.valueOf(source.retryAttempts()))); }
            @Override public long retryDelay() { return Long.parseLong(resolveEnv("REST_CLIENT_RETRY_DELAY_" + bindingName, String.valueOf(source.retryDelay()))); }
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the binding name from the {@code @Named} annotation on the field.
     * Falls back to the endpoint URL if no {@code @Named} is present.
     */
    static String resolveBindingName(Field field)
    {
        Named named = field.getAnnotation(Named.class);
        if (named != null && !named.value().isEmpty()) return named.value();
        Endpoint endpoint = field.getAnnotation(Endpoint.class);
        if (endpoint != null) return endpoint.url();
        return field.getName();
    }

    static String resolveUrl(String value)
    {
        return RestClient.resolveEnvPlaceholder(value);
    }

    private static String resolveEnv(String envKey, String defaultValue)
    {
        return com.guicedee.client.Environment.getSystemPropertyOrEnvironment(envKey, defaultValue);
    }

    private static Type[] extractTypeArguments(Field field)
    {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType pt)
        {
            Type[] typeArgs = pt.getActualTypeArguments();
            Type send = typeArgs.length > 0 ? typeArgs[0] : Object.class;
            Type receive = typeArgs.length > 1 ? typeArgs[1] : Object.class;
            return new Type[]{send, receive};
        }
        return new Type[]{Object.class, Object.class};
    }

    static Class<?> toRawClass(Type type)
    {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType pt)
        {
            Type raw = pt.getRawType();
            if (raw instanceof Class<?> clazz) return clazz;
        }
        return Object.class;
    }
}
