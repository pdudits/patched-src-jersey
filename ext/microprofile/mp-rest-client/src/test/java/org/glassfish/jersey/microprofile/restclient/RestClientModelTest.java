/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.microprofile.restclient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author David Kral
 * @author Patrik Dudits
 */
public class RestClientModelTest extends JerseyTest {
    @Override
    protected ResourceConfig configure() {
        enable(TestProperties.LOG_TRAFFIC);
        return new ResourceConfig(ApplicationResourceImpl.class);
    }

    @Test
    public void testGetIt() throws URISyntaxException {
        ApplicationResource app = RestClientBuilder.newBuilder()
                .baseUri(new URI("http://localhost:9998"))
                .build(ApplicationResource.class);
        assertEquals("This is default value!", app.getValue());
        assertEquals("Hi", app.sayHi());
    }

    @Test
    public void testList() {
        ApplicationResource app = RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:9998"))
                .build(ApplicationResource.class);
        assertEquals(Arrays.asList("int1", "int2", "int3"), app.list(3));
    }

    @Test
    public void testMap() {
        ApplicationResource app = RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:9998"))
                .build(ApplicationResource.class);
        Map<String, Integer> map = app.map(20);
        assertEquals(20, map.size());
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().toString());
        }
    }

    @Test
    public void testListParameter() {
        ApplicationResource app = RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:9998"))
                .build(ApplicationResource.class);
        Map<String, Integer> result = app.acceptList(Arrays.asList(1, 2, 3, 4, 5));
        assertEquals(5, result.get("size").intValue());
    }
}
