import com.google.inject.gee.InjectionPointProvider;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.rest.client.RestClientConfigurator;
import com.guicedee.rest.client.implementations.RestClientBinder;
import com.guicedee.rest.client.implementations.RestClientInjectionPointProvision;
import com.guicedee.rest.client.implementations.RestClientPreStartup;

module com.guicedee.rest.client {
    exports com.guicedee.rest.client;
    exports com.guicedee.rest.client.annotations;
    exports com.guicedee.rest.client.implementations;

    requires transitive com.guicedee.client;
    requires io.vertx.web.client;
    requires io.vertx.core;

    requires transitive com.guicedee.jsonrepresentation;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.core;

    requires com.google.guice;

    requires io.github.classgraph;
    requires org.apache.logging.log4j.core;
    requires static lombok;
    requires io.smallrye.mutiny;
    requires io.vertx.web.common;
    requires jakarta.inject;
    requires com.guicedee.vertx;

    opens com.guicedee.rest.client to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.rest.client.annotations to com.google.guice, com.fasterxml.jackson.databind;
    opens com.guicedee.rest.client.implementations to com.google.guice, com.fasterxml.jackson.databind;

    provides IGuiceModule with RestClientBinder;
    provides IGuicePreStartup with RestClientPreStartup;
    provides InjectionPointProvider with RestClientInjectionPointProvision;

    uses RestClientConfigurator;
}


