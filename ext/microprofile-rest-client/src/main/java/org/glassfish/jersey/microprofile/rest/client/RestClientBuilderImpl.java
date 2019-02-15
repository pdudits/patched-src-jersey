/*
 * Copyright (c) 2018 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package org.glassfish.jersey.microprofile.rest.client;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.opentracing.Traced;
import org.glassfish.jersey.microprofile.rest.client.ext.DefaultResponseExceptionMapper;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.glassfish.jersey.client.proxy.WebResourceFactory;
import static org.glassfish.jersey.microprofile.rest.client.Constant.DISABLE_DEFAULT_EXCEPTION_MAPPER;
import org.glassfish.jersey.MPConfig;
import static java.lang.Boolean.FALSE;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RestClientBuilderImpl implements RestClientBuilder {

    private static final Logger logger = Logger.getLogger(RestClientBuilderImpl.class.getName());

    private URI baseUri;

    private final ClientBuilder clientBuilder;

    public RestClientBuilderImpl() {
        clientBuilder = ClientBuilder.newBuilder();
    }

    @Override
    public RestClientBuilder baseUrl(URL url) {
        try {
            this.baseUri = url.toURI();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(
                    String.format("Rest Client url is invalid [%s] ", url), ex
            );
        }
        return this;
    }

    @Override
    public RestClientBuilder baseUri(URI uri) {
        this.baseUri = uri;
        return this;
    }

    @Override
    public RestClientBuilder executorService(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ExecutorService is null");
        }
        clientBuilder.executorService(executor);
        return this;
    }

    @Override
    public <T> T build(Class<T> restClientInterface) throws IllegalStateException, RestClientDefinitionException {
        if (baseUri == null) {
            throw new IllegalStateException("Base URI or URL can't be null");
        }

        // interface validity
        RestClientValidator.getInstance().validate(restClientInterface);

        registerDefaultExceptionMapper();
        registerProviders(restClientInterface);

        Client client =  clientBuilder.build();

        client.property("skipTracingOn", createRestClientSkipMethodTracingMap(baseUri, restClientInterface));

        WebTarget webTarget = client.target(baseUri);

        return WebResourceFactory.newResource(restClientInterface, webTarget);
    }

    private void registerDefaultExceptionMapper() {
        // Default exception mapper check per client basis
        Object disableDefaultExceptionMapperProp = getConfiguration()
                .getProperty(DISABLE_DEFAULT_EXCEPTION_MAPPER);
        if (disableDefaultExceptionMapperProp == null) {
            //check MicroProfile Config
            boolean disableDefaultExceptionMapper =
                    MPConfig.getOptionalValue(DISABLE_DEFAULT_EXCEPTION_MAPPER, Boolean.class)
                    .orElse(FALSE);
            if (!disableDefaultExceptionMapper) {
                register(DefaultResponseExceptionMapper.class);
            }
        } else if (FALSE.equals(disableDefaultExceptionMapperProp)) {
            register(DefaultResponseExceptionMapper.class);
        }
    }

    private <T> void registerProviders(Class<T> restClient) {
        RegisterProvider[] providers = restClient.getAnnotationsByType(RegisterProvider.class);
        for (RegisterProvider provider : providers) {
            register(provider.value(), provider.priority());
        }
    }

    @Override
    public Configuration getConfiguration() {
        return clientBuilder.getConfiguration();
    }

    @Override
    public RestClientBuilder property(String name, Object value) {
        clientBuilder.property(name, value);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> componentClass) {
        clientBuilder.register(componentClass);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> type, int priority) {
        clientBuilder.register(type, priority);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> type, Class<?>... contracts) {
        clientBuilder.register(type, contracts);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> type, Map<Class<?>, Integer> contracts) {
        clientBuilder.register(type, contracts);
        return this;
    }

    @Override
    public RestClientBuilder register(Object component) {
        clientBuilder.register(component);
        return this;
    }

    @Override
    public RestClientBuilder register(Object component, int priority) {
        clientBuilder.register(component, priority);
        return this;
    }

    @Override
    public RestClientBuilder register(Object component, Class<?>... contracts) {
        clientBuilder.register(component, contracts);
        return this;
    }

    @Override
    public RestClientBuilder register(Object component, Map<Class<?>, Integer> contracts) {
        clientBuilder.register(component, contracts);
        return this;
    }

    private <T> Map<String, String> createRestClientSkipMethodTracingMap(URI baseUri, Class<T> restClientInterface) {
        Map<String, String> skipMethodTracingMap = new HashMap<>();

        Config config = null;

        try {
            config = ConfigProvider.getConfig();
        } catch (IllegalArgumentException ex) {
            logger.log(Level.INFO, "No config could be found", ex);
        }

        // Get the class level Path value
        String classLevelPathValue = "";
        Path classLevelPathAnnotation = restClientInterface.getAnnotation(Path.class);
        if (classLevelPathAnnotation != null) {
            classLevelPathValue = classLevelPathAnnotation.value();
        }

        // Check if there's a config override for the method
        if (config != null) {
            // Get the annotation and class names
            String annotationName = Traced.class.getSimpleName();
            String annotatedClassCanonicalName = restClientInterface.getCanonicalName();

            // First, construct the base URL path, checking for any config overrides on the class-level annotation
            String jaxrsMethodEndpoint = baseUri.getPath();
            Optional<String> pathOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                    + Path.class.getSimpleName() + "/" + "value", String.class);
            if (pathOverride.isPresent()) {
                jaxrsMethodEndpoint += pathOverride.get();
            } else {
                jaxrsMethodEndpoint += classLevelPathValue;
            }

            for (Method method : restClientInterface.getMethods()) {
                // Check that the method is annotated with a JAX-RS method
                String httpMethod = getHttpMethodName(method);
                if (httpMethod == null) {
                    logger.log(Level.FINER, "Method does not appear to be a JAX-RS method.");
                    // If this method isn't annotated with any HTTP method, skip it as it isn't a JAX-RS method.
                    continue;
                }

                String annotatedMethodName = method.getName();
                boolean shouldSkipTracing = false;

                // Look for an override to the traced annotation on the method
                logger.log(Level.FINER, "Getting config override for annotated method...");
                Optional<Boolean> tracedOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                        + annotatedMethodName + "/" + annotationName + "/" + "value", boolean.class);

                // If there isn't a config override for the method, check if the method has the annotation
                if (!tracedOverride.isPresent()) {
                    logger.log(Level.FINER, "No config override for annotated method, checking if the method is "
                            + "annotated directly...");
                    // If the method is annotated directly, simply use the value from it and move on
                    if (method.getAnnotation(Traced.class) != null) {
                        logger.log(Level.FINER, "Traced annotation found on method.");
                        // If the method is annotated, check if it's explicitly been set to skip tracing
                        if (!method.getAnnotation(Traced.class).value()) {
                            // If we should skip tracing on this method, try to get the path annotation from the method
                            // after checking if the path annotation has any overrides
                            pathOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                                    + annotatedMethodName + "/" + Path.class.getSimpleName() + "/" + "value",
                                    String.class);
                            if (pathOverride.isPresent()) {
                                jaxrsMethodEndpoint += pathOverride.get();
                            } else {
                                Path pathAnnotation = method.getAnnotation(Path.class);
                                if (pathAnnotation != null) {
                                    logger.log(Level.FINER, "Path annotation found on method.");
                                    jaxrsMethodEndpoint += pathAnnotation.value();
                                }
                            }

                            shouldSkipTracing = true;
                        }
                    } else {
                        // If there wasn't a config override for the method and the method wasn't annotated directly,
                        // check if there's one for the class
                        logger.log(Level.FINER, "No config override for annotated method, getting config override "
                                + "for the annotated class...");
                        tracedOverride = config.getOptionalValue(annotatedClassCanonicalName + "/" + annotationName
                                + "/" + "value", boolean.class);

                        // If we found a config override, and it disables the method, add it to the list
                        if (tracedOverride.isPresent()) {
                            logger.log(Level.FINER, "Config override found on the annotated class.");
                            if (!tracedOverride.get()) {
                                // Since the method doesn't have anything to override this class-level setting, add
                                // it to the skip list after determining if it has its own path
                                pathOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                                        + annotatedMethodName + "/" + Path.class.getSimpleName() + "/"
                                        + "value", String.class);
                                if (pathOverride.isPresent()) {
                                    jaxrsMethodEndpoint += pathOverride.get();
                                } else {
                                    Path pathAnnotation = method.getAnnotation(Path.class);
                                    if (pathAnnotation != null) {
                                        logger.log(Level.FINER, "Path annotation found on method.");
                                        jaxrsMethodEndpoint += pathAnnotation.value();
                                    }
                                }

                                shouldSkipTracing = true;
                            }
                        } else {
                            // If we didn't find a config override, check if the class is annotated and use the value
                            // from that if it is
                            Traced tracedAnnotation = restClientInterface.getAnnotation(Traced.class);
                            if (tracedAnnotation != null) {
                                logger.log(Level.FINER, "Traced annotation found on class.");
                                if (!tracedAnnotation.value()) {
                                    // Since the method doesn't have anything to override this class-level setting, add
                                    // it to the skip list after determining if it has its own path
                                    pathOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                                                    + annotatedMethodName + "/" + Path.class.getSimpleName() + "/"
                                                    + "value", String.class);
                                    if (pathOverride.isPresent()) {
                                        jaxrsMethodEndpoint += pathOverride.get();
                                    } else {
                                        Path pathAnnotation = method.getAnnotation(Path.class);
                                        if (pathAnnotation != null) {
                                            logger.log(Level.FINER, "Path annotation found on method.");
                                            jaxrsMethodEndpoint += pathAnnotation.value();
                                        }
                                    }

                                    shouldSkipTracing = true;
                                }
                            }
                        }
                    }
                } else {
                    logger.log(Level.FINER, "Config override found on the annotated method.");
                    // If we found a config override on the method, use the value from this
                    if (!tracedOverride.get()) {
                        // Get the path to use
                        pathOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                                + annotatedMethodName + "/" + Path.class.getSimpleName() + "/"
                                + "value", String.class);
                        if (pathOverride.isPresent()) {
                            jaxrsMethodEndpoint += pathOverride.get();
                        } else {
                            Path pathAnnotation = method.getAnnotation(Path.class);
                            if (pathAnnotation != null) {
                                logger.log(Level.FINER, "Path annotation found on method.");
                                jaxrsMethodEndpoint += pathAnnotation.value();
                            }
                        }

                        shouldSkipTracing = true;
                    }
                }
            }
        } else {
            logger.log(Level.FINER, "No config to get override parameters from. Just checking annotations directly...");

            for (Method method : restClientInterface.getMethods()) {
                boolean shouldSkipTracing = false;

                // Check that the method is annotated with a JAX-RS method
                String httpMethod = getHttpMethodName(method);
                if (httpMethod == null) {
                    logger.log(Level.FINER, "Method does not appear to be a JAX-RS method.");
                    // If this method isn't annotated with any HTTP method, skip it as it isn't a JAX-RS method.
                    continue;
                }

                String jaxrsMethodEndpoint = baseUri.getPath() + classLevelPathValue;

                // Check if the method is annotated directly
                Traced tracedAnnotation = method.getAnnotation(Traced.class);
                if (tracedAnnotation != null) {
                    logger.log(Level.FINER, "Traced annotation found on method.");
                    // If the method is annotated, check if it's explicitly been set to skip tracing
                    if (!tracedAnnotation.value()) {
                        // If we should skip tracing on this method, try to get the path annotation from the method
                        Path pathAnnotation = method.getAnnotation(Path.class);
                        if (pathAnnotation != null) {
                            logger.log(Level.FINER, "Path annotation found on method.");
                            jaxrsMethodEndpoint += pathAnnotation.value();
                        }

                        shouldSkipTracing = true;
                    }
                } else {
                    logger.log(Level.FINER, "Traced annotation not found on method, checking class");
                    // If the method wasn't annotated directly, check if the class is
                    tracedAnnotation = restClientInterface.getAnnotation(Traced.class);
                    if (tracedAnnotation != null) {
                        logger.log(Level.FINER, "Traced annotation found on class.");
                        if (!tracedAnnotation.value()) {
                            // Since the method doesn't have anything to override this class-level setting, add it to the
                            // skip list after determining if it has its own path
                            Path pathAnnotation = method.getAnnotation(Path.class);
                            if (pathAnnotation != null) {
                                logger.log(Level.FINER, "Path annotation found on method.");
                                jaxrsMethodEndpoint += pathAnnotation.value();
                            }

                            shouldSkipTracing = true;
                        }
                    }
                }

                if (shouldSkipTracing) {
                    logger.log(Level.FINER, "Adding endpoint " + jaxrsMethodEndpoint + ":" + httpMethod
                            + " to skip map.");
                    skipMethodTracingMap.put(jaxrsMethodEndpoint, httpMethod);
                }
            }
        }

        return skipMethodTracingMap;
    }

    /**
     * Helper method that returns a String representing the name of the HTTP method annotation that the client method is
     * annotated with, or null if it isn't annotated.
     *
     * @param method The method to get the annotated HTTP method off of
     * @return The name of the HTTP method that the client method is annotated with, or null if no annotation found.
     */
    private String getHttpMethodName(Method method) {
        // Initialise an Array with all supported JaxRs HTTP methods
        Class[] httpMethods = {GET.class, POST.class, DELETE.class, PUT.class, HEAD.class, PATCH.class, OPTIONS.class};

        // Check if any of the HTTP Method annotations are present on the intercepted method
        for (Class httpMethod : httpMethods) {
            if (method.getAnnotation(httpMethod) != null) {
                return httpMethod.getSimpleName();
            }
        }

        return null;
    }

}
