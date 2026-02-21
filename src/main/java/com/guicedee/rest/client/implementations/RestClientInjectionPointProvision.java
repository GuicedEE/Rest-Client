package com.guicedee.rest.client.implementations;

import com.google.inject.gee.InjectionPointProvider;
import com.guicedee.rest.client.annotations.Endpoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

public class RestClientInjectionPointProvision implements InjectionPointProvider {

    @Override
    public Class<? extends Annotation> injectionPoint(AnnotatedElement member) {
        return Endpoint.class;
    }

}
