open module com.guicedee.rest.client.test {
    requires transitive com.guicedee.rest.client;
    requires com.guicedee.guicedinjection;
    requires com.google.guice;
    requires io.vertx.core;

    requires org.junit.jupiter;

    exports com.guicedee.rest.client.test;
    exports com.guicedee.rest.client.test.classurls;

}