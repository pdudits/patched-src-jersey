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

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that provides methods for instrumenting the rest client via OpenTracing.
 *
 * @author Andrew Pielage <andrew.pielage@payara.fish>
 */
public class OpenTracingInstrumentation {

    private static final Logger logger = Logger.getLogger(RestClientBuilderImpl.class.getName());

    /**
     * Creates a Map of JAX-RS endpoints that tracing should be skipped on.
     * @param baseUri The base URI of the JAX-RS application
     * @param restClientInterface The MicroProfile Rest Client interface to create the skip map for
     * @param <T> The Class of the MicroProfile Rest Client interface
     * @return A Map of JAX-RS endpoints that tracing should be skipped on.
     */
    protected static <T> Map<String, String> createRestClientSkipMethodTracingMap(URI baseUri,
            Class<T> restClientInterface) {
        Map<String, String> skipTracingMap = new HashMap<>();

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
            populateSkipTracingMapUsingConfig(config, classLevelPathValue, baseUri, restClientInterface,
                    skipTracingMap);
        } else {
            populateSkipTracingMap(classLevelPathValue, baseUri, restClientInterface, skipTracingMap);
        }

        return skipTracingMap;
    }

    /**
     * Helper method that populates the map of JAX-RS endpoints to skip, checking a config for any overrides.
     *
     * @param config The config to check for overrides from
     * @param classLevelPathValue The value of the Path variable that the class is annotated with
     * @param baseUri THe base URI of the JAX-RS application
     * @param restClientInterface The MicroProfile Rest Client interface to create the skip map for
     * @param skipTracingMap The map of JAX-RS endpoints that should be skipped
     * @param <T> The Class of the MicroProfile Rest Client interface
     */
    private static <T> void populateSkipTracingMapUsingConfig(Config config, String classLevelPathValue, URI baseUri,
            Class<T> restClientInterface, Map<String, String> skipTracingMap) {
        // Get the class name
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
                // If this method isn't annotated with any HTTP method, skip it as it isn't a JAX-RS method.
                logger.log(Level.FINER, "Method does not appear to be a JAX-RS method.");
                continue;
            }

            String annotatedMethodName = method.getName();
            boolean shouldSkipTracing = false;

            // Look for an override to the traced annotation on the method
            logger.log(Level.FINER, "Getting config override for annotated method...");
            Optional<Boolean> tracedOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                    + annotatedMethodName + "/" + Traced.class.getSimpleName() + "/" + "value", boolean.class);

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
                        jaxrsMethodEndpoint = getMethodLevelPathValueIfPresentUsingConfig(config, method,
                                annotatedClassCanonicalName, annotatedMethodName, jaxrsMethodEndpoint);

                        shouldSkipTracing = true;
                    }
                } else {
                    // If there wasn't a config override for the method and the method wasn't annotated directly,
                    // check if there's one for the class
                    logger.log(Level.FINER, "No config override for annotated method, getting config override "
                            + "for the annotated class...");
                    tracedOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                            + Traced.class.getSimpleName() + "/" + "value", boolean.class);

                    // If we found a config override, and it disables the method, add it to the list
                    if (tracedOverride.isPresent()) {
                        logger.log(Level.FINER, "Config override found on the annotated class.");
                        if (!tracedOverride.get()) {
                            // Since the method doesn't have anything to override this class-level setting, add
                            // it to the skip list after determining if it has its own path
                            jaxrsMethodEndpoint = getMethodLevelPathValueIfPresentUsingConfig(config, method,
                                    annotatedClassCanonicalName, annotatedMethodName, jaxrsMethodEndpoint);

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
                                jaxrsMethodEndpoint = getMethodLevelPathValueIfPresentUsingConfig(config, method,
                                        annotatedClassCanonicalName, annotatedMethodName, jaxrsMethodEndpoint);

                                shouldSkipTracing = true;
                            }
                        }
                    }
                }
            } else {
                logger.log(Level.FINER, "Config override found on the annotated method.");
                // If we found a config override on the method, use the value from this
                if (!tracedOverride.get()) {
                    // Get the path to use from either the method or class, checking for config overrides
                    jaxrsMethodEndpoint = getMethodLevelPathValueIfPresentUsingConfig(config, method,
                            annotatedClassCanonicalName, annotatedMethodName, jaxrsMethodEndpoint);

                    shouldSkipTracing = true;
                }
            }

            if (shouldSkipTracing) {
                logger.log(Level.FINER, "Adding endpoint " + jaxrsMethodEndpoint + ":" + httpMethod
                        + " to skip map.");
                skipTracingMap.put(jaxrsMethodEndpoint, httpMethod);
            }
        }
    }

    /**
     * Helper method that populates the map of JAX-RS endpoints to skip.
     *
     * @param classLevelPathValue The value of the Path variable that the class is annotated with
     * @param baseUri The base URI of the
     * @param restClientInterface The MicroProfile Rest Client interface to create the skip map for
     * @param skipTracingMap The map of JAX-RS endpoints that should be skipped
     * @param <T> The Class of the MicroProfile Rest Client interface
     */
    private static <T> void populateSkipTracingMap(String classLevelPathValue, URI baseUri,
            Class<T> restClientInterface, Map<String, String> skipTracingMap) {
        logger.log(Level.FINER, "No config to get override parameters from. Just checking annotations directly...");

        for (Method method : restClientInterface.getMethods()) {
            boolean shouldSkipTracing = false;

            // Check that the method is annotated with a JAX-RS method
            String httpMethod = getHttpMethodName(method);
            if (httpMethod == null) {
                // If this method isn't annotated with any HTTP method, skip it as it isn't a JAX-RS method.
                logger.log(Level.FINER, "Method does not appear to be a JAX-RS method.");
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
                    jaxrsMethodEndpoint = getMethodLevelPathValueIfPresent(method, jaxrsMethodEndpoint);

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
                        jaxrsMethodEndpoint = getMethodLevelPathValueIfPresent(method, jaxrsMethodEndpoint);

                        shouldSkipTracing = true;
                    }
                }
            }

            if (shouldSkipTracing) {
                logger.log(Level.FINER, "Adding endpoint " + jaxrsMethodEndpoint + ":" + httpMethod
                        + " to skip map.");
                skipTracingMap.put(jaxrsMethodEndpoint, httpMethod);
            }
        }
    }

    /**
     * Helper method that gets the Path annotation value from a provided method if present and appends its value to
     * the jaxrsMethodEndpoint variable, using a config override if one is present.
     *
     * @param config The Config to check for an override from.
     * @param method The method to check for the Path annotation on
     * @param annotatedClassCanonicalName The name of the class that we're checking for overrides on
     * @param annotatedMethodName The name of the method that we're checking for overrides on
     * @param jaxrsMethodEndpoint The String being used to construct the JAX-RS Endpoint to skip
     * @return The String being used to construct the JAX-RS Endpoint to skip
     */
    private static String getMethodLevelPathValueIfPresentUsingConfig(Config config, Method method,
            String annotatedClassCanonicalName, String annotatedMethodName, String jaxrsMethodEndpoint) {
        Optional<String> pathOverride = config.getOptionalValue(annotatedClassCanonicalName + "/"
                + annotatedMethodName + "/" + Path.class.getSimpleName() + "/" + "value", String.class);
        if (pathOverride.isPresent()) {
            jaxrsMethodEndpoint += pathOverride.get();
        } else {
            jaxrsMethodEndpoint = getMethodLevelPathValueIfPresent(method, jaxrsMethodEndpoint);
        }

        return jaxrsMethodEndpoint;
    }

    /**
     * Helper method that gets the Path annotation value from a provided method if present and appends its value to
     * the jaxrsMethodEndpoint variable.
     * @param method The method to check for the Path annotation on
     * @param jaxrsMethodEndpoint The String being used to construct the JAX-RS Endpoint to skip
     * @return c
     */
    private static String getMethodLevelPathValueIfPresent(Method method, String jaxrsMethodEndpoint) {
        Path pathAnnotation = method.getAnnotation(Path.class);
        if (pathAnnotation != null) {
            logger.log(Level.FINER, "Path annotation found on method.");
            jaxrsMethodEndpoint += pathAnnotation.value();
        }

        return jaxrsMethodEndpoint;
    }

    /**
     * Helper method that returns a String representing the name of the HTTP method annotation that the client method is
     * annotated with, or null if it isn't annotated.
     *
     * @param method The method to get the annotated HTTP method off of
     * @return The name of the HTTP method that the client method is annotated with, or null if no annotation found.
     */
    private static String getHttpMethodName(Method method) {
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
