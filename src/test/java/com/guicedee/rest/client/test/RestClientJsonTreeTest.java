package com.guicedee.rest.client.test;

import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.RestClientException;
import com.guicedee.rest.client.annotations.Endpoint;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP and configured client decoding; no application identities or database. */
public class RestClientJsonTreeTest {
    @Endpoint(url="http://localhost:" + TestHttpServer.PORT + "/json-object")
    @Named("jsonObjectDocument") private RestClient<Void, JsonObject> object;
    @Endpoint(url="http://localhost:" + TestHttpServer.PORT + "/json-array")
    @Named("jsonArrayDocument") private RestClient<Void, JsonArray> array;
    @Endpoint(url="http://localhost:" + TestHttpServer.PORT + "/json-invalid")
    @Named("jsonInvalidDocument") private RestClient<Void, JsonObject> invalid;
    @Endpoint(url="http://localhost:" + TestHttpServer.PORT + "/json-array")
    @Named("jsonWrongObjectShape") private RestClient<Void, JsonObject> wrongShape;

    @BeforeAll static void start() throws Exception {
        TestHttpServer.ensureStarted();
        IGuiceContext.registerModule("com.guicedee.rest.client.test");
        IGuiceContext.instance().inject();
    }
    @Test void preservesObjectFieldsEmptyArraysNullsAndNestedValues() {
        JsonObject value = IGuiceContext.get(RestClientJsonTreeTest.class).object.send().await().atMost(Duration.ofSeconds(10));
        assertEquals(Set.of("friends", "next", "detail", "revision"), value.fieldNames());
        assertTrue(value.getJsonArray("friends").isEmpty());
        assertNull(value.getValue("next"));
        assertTrue(value.getJsonObject("detail").getBoolean("active"));
        assertEquals(7, value.getInteger("revision"));
    }
    @Test void preservesArrayElements() {
        JsonArray value = IGuiceContext.get(RestClientJsonTreeTest.class).array.send().await().atMost(Duration.ofSeconds(10));
        assertEquals(3, value.size());
        assertTrue(value.getJsonObject(0).getBoolean("active"));
        assertNull(value.getValue(1));
        assertEquals(7, value.getInteger(2));
    }
    @Test void rejectsMalformedAndWrongShapeDocuments() {
        var client = IGuiceContext.get(RestClientJsonTreeTest.class);
        assertThrows(RestClientException.class, () -> client.invalid.send().await().atMost(Duration.ofSeconds(10)));
        assertThrows(RestClientException.class, () -> client.wrongShape.send().await().atMost(Duration.ofSeconds(10)));
    }
}
