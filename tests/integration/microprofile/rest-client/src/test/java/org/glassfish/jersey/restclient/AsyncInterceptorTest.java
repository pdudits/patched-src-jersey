/*
 *    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *    Copyright (c) [2019] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *    The contents of this file are subject to the terms of either the GNU
 *    General Public License Version 2 only ("GPL") or the Common Development
 *    and Distribution License("CDDL") (collectively, the "License").  You
 *    may not use this file except in compliance with the License.  You can
 *    obtain a copy of the License at
 *    https://github.com/payara/Payara/blob/master/LICENSE.txt
 *    See the License for the specific
 *    language governing permissions and limitations under the License.
 *
 *    When distributing the software, include this License Header Notice in each
 *    file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *    GPL Classpath Exception:
 *    The Payara Foundation designates this particular file as subject to the "Classpath"
 *    exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *    file that accompanied this code.
 *
 *    Modifications:
 *    If applicable, add the following below the License Header, with the fields
 *    enclosed by brackets [] replaced by your own identifying information:
 *    "Portions Copyright [year] [name of copyright owner]"
 *
 *    Contributor(s):
 *    If you wish your version of this file to be governed by only the CDDL or
 *    only the GPL Version 2, indicate your decision by adding "[Contributor]
 *    elects to include this software in this distribution under the [CDDL or GPL
 *    Version 2] license."  If you don't indicate a single choice of license, a
 *    recipient has the option to distribute your version of this file under
 *    either the CDDL, the GPL Version 2 or to extend the choice of license to
 *    its licensees as provided above.  However, if you add GPL Version 2 code
 *    and therefore, elected the GPL Version 2 license, then the option applies
 *    only if the new code is made subject to such option by the copyright
 *    holder.
 */

package org.glassfish.jersey.restclient;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptor;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;

public class AsyncInterceptorTest extends JerseyTest {
    static ThreadLocal<String> trace = new ThreadLocal<>();

    @Rule
    public TestName name = new TestName();

    @Override
    protected Application configure() {
        return new ResourceConfig(AsyncResourceImpl.class);
    }

    @Test
    public void testPropagation() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
        FailsOn mode = FailsOn.NEVER;
        AsyncResource client = createClient(mode);

        trace.set(name.getMethodName());
        CompletableFuture<String> future = client.asyncCall().toCompletableFuture();
        String result = future.get(1, TimeUnit.SECONDS);
        assertEquals(name.getMethodName(), result);
    }

    @Test
    public void testFailingOnPrepare() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
        FailsOn mode = FailsOn.PREPARE;
        AsyncResource client = createClient(mode);

        trace.set(name.getMethodName());
        CompletableFuture<String> future = client.asyncCall().toCompletableFuture();
        try {
            String result = future.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // that's what we expect
            Throwable cause = e.getCause().getCause();
            assertEquals("Fails on prepare", cause.getMessage());
        }
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void testFailingOnApply() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
        FailsOn mode = FailsOn.APPLY;
        AsyncResource client = createClient(mode);

        trace.set(name.getMethodName());
        CompletableFuture<String> future = client.asyncCall().toCompletableFuture();
        try {
            String result = future.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // that's what we expect
            Throwable cause = e.getCause().getCause();
            assertEquals("Fails on apply", cause.getMessage());
        }
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void testFailingOnRemove() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
        FailsOn mode = FailsOn.REMOVE;
        AsyncResource client = createClient(mode);

        trace.set(name.getMethodName());
        CompletableFuture<String> future = client.asyncCall().toCompletableFuture();
        try {
            String result = future.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // that's what we expect
            Throwable cause = e.getCause().getCause();
            assertEquals("Fails on remove", cause.getMessage());
        }
        assertTrue(future.isCompletedExceptionally());
    }

    private AsyncResource createClient(FailsOn mode) throws URISyntaxException {
        return RestClientBuilder.newBuilder()
                .baseUri(new URI("http://localhost:9998"))
                .register(interceptor(mode))
                .register(TracePropagator.class)
                .build(AsyncResource.class);
    }

    private AsyncInvocationInterceptorFactory interceptor(FailsOn mode) {
        return () -> new FailingInterceptor(mode);
    }

    enum FailsOn {
        NEVER, PREPARE, APPLY, REMOVE
    }

    static class TracePropagator implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            requestContext.getHeaders().add("trace", trace.get());
        }
    }

    static class FailingInterceptor implements AsyncInvocationInterceptor {
        private final FailsOn mode;
        private String traceValue;

        FailingInterceptor(FailsOn mode) {
            this.mode = mode;
        }

        @Override
        public void prepareContext() {
            if (mode == FailsOn.PREPARE) {
                throw new AssertionError("Fails on prepare");
            }
            traceValue = trace.get();
        }

        @Override
        public void applyContext() {
            if (mode == FailsOn.APPLY) {
                throw new AssertionError("Fails on apply");
            }
            trace.set(traceValue);
        }

        @Override
        public void removeContext() {
            if (mode == FailsOn.REMOVE) {
                throw new AssertionError("Fails on remove");
            }
            trace.remove();
        }
    }
}
