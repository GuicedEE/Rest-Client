package com.guicedee.rest.client.test;

import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.annotations.Endpoint;
import com.guicedee.rest.client.annotations.EndpointOptions;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class RestClientTest {

    @SuppressWarnings("BindingAnnotationWithoutInject")
    @Endpoint(url = "http://localhost:" + TestHttpServer.PORT + "/test",
            method = "GET",
            options = @EndpointOptions(readTimeout = 5000)
    )
    @Named("testGet")
    private RestClient<String, String> restClient;

    @BeforeAll
    static void before() throws Exception {
        TestHttpServer.ensureStarted();
        IGuiceContext.registerModule("com.guicedee.rest.client.test");
        IGuiceContext.instance().inject();
    }

    @Test
    public void testRestHttpCall() {
        RestClientTest instance = IGuiceContext.get(RestClientTest.class);
        String result = instance.restClient.send()
                .onFailure().invoke(err -> fail("REST call failed: " + err.getMessage()))
                .await().atMost(java.time.Duration.ofSeconds(10));
        assertNotNull(result);
        assertEquals("hello from test server", result);
        System.out.println("Got: " + result);
    }
}
