package com.guicedee.rest.client.test.classurls;

import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.annotations.Endpoint;
import com.guicedee.rest.client.annotations.EndpointOptions;
import com.guicedee.rest.client.test.TestHttpServer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class RestClientInheritedEndpointPropertiesTest {
    @SuppressWarnings("BindingAnnotationWithoutInject")
    @Endpoint(url = "/test",
            method = "GET",
            options = @EndpointOptions(readTimeout = 5000)
    )
    @Named("testGetInheritedEndpointConfigs")
    private RestClient<String, String> restClient;

    @BeforeAll
    static void before() throws Exception {
        TestHttpServer.ensureStarted();
        IGuiceContext.registerModule("com.guicedee.rest.client.test");
        IGuiceContext.instance().inject();
    }

    @Test
    public void testRestHttpCall() {
        //this url should be the package (or closest package-info) url concatenated with /test
        RestClientInheritedEndpointPropertiesTest instance = IGuiceContext.get(RestClientInheritedEndpointPropertiesTest.class);
        String result = instance.restClient.send("Hello")
                .onFailure().invoke(err -> fail("REST call failed: " + err.getMessage()))
                .await().atMost(java.time.Duration.ofSeconds(10));
        assertNotNull(result);
        System.out.println("Got: " + result);
    }

}
