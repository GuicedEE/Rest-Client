package com.guicedee.rest.client.test;

import com.google.inject.name.Named;
import com.guicedee.client.IGuiceContext;
import com.guicedee.rest.client.RestClient;
import com.guicedee.rest.client.annotations.Endpoint;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RestClientTest {
    @SuppressWarnings("BindingAnnotationWithoutInject")
    @Endpoint(url = "/test",
            method = "GET"
    )
    @Named("testGet")
    private RestClient<String, String> restClient;

    @BeforeAll
    static void before() {
        IGuiceContext.registerModule("com.guicedee.rest.client.test");
        IGuiceContext.instance().inject();

        //todo setup a basic http server for the tests, shut it all down after()
    }

    @Test
    public void testRestHttpCall() {
        RestClientTest instance = IGuiceContext.get(RestClientTest.class);
        instance.restClient.send()
                .onItemOrFailure().call((result, err) -> {
                    if (result != null) {
                        System.out.println("Got: " + result);
                    } else {
                        System.err.println("Failed: " + err.getMessage());
                    }
                    return Uni.createFrom().item(true);
                })
                .await().indefinitely();
    }

}
