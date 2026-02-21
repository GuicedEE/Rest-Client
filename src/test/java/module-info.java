open module com.guicedee.rest.client.test {
    requires transitive com.guicedee.rest.client;
    requires com.guicedee.guicedinjection;
    requires com.google.guice;

    requires org.junit.jupiter;

    exports com.guicedee.rest.client.test;

}