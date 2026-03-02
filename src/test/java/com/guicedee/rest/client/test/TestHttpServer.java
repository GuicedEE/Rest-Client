package com.guicedee.rest.client.test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared lightweight HTTP server for REST client tests.
 * <p>
 * Call {@link #ensureStarted()} from {@code @BeforeAll} — the server starts once
 * and is reused across test classes. Shutdown is registered via a JVM shutdown hook.
 */
public final class TestHttpServer {

    public static final int PORT = 4580;

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static Vertx vertx;
    private static HttpServer server;

    private TestHttpServer() {}

    /**
     * Starts the test HTTP server if it isn't already running.
     * Safe to call from multiple test classes.
     */
    public static void ensureStarted() throws Exception {
        if (!started.compareAndSet(false, true)) {
            return; // already running
        }

        vertx = Vertx.vertx();
        CountDownLatch latch = new CountDownLatch(1);

        server = vertx.createHttpServer();
        server.requestHandler(req -> {
            String path = req.path();
            switch (path) {
                case "/test" ->
                    req.response()
                       .putHeader("Content-Type", "text/plain")
                       .end("hello from test server");
                case "/classurls/test" ->
                    req.response()
                       .putHeader("Content-Type", "text/plain")
                       .end("hello from classurls");
                default ->
                    req.response()
                       .setStatusCode(404)
                       .end("Not Found: " + path);
            }
        });
        server.listen(PORT)
              .onComplete(ar -> latch.countDown());

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test HTTP server failed to start on port " + PORT);
        }

        // Shutdown hook so the server stops when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { shutdown(); } catch (Exception ignored) {}
        }));
    }

    /**
     * Stops the test HTTP server and Vert.x instance.
     */
    public static void shutdown() throws Exception {
        if (server != null) {
            CountDownLatch latch = new CountDownLatch(1);
            server.close().onComplete(ar -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
            server = null;
        }
        if (vertx != null) {
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close().onComplete(ar -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
            vertx = null;
        }
        started.set(false);
    }
}


