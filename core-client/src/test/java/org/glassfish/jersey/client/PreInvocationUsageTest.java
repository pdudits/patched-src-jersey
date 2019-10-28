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

package org.glassfish.jersey.client;

import org.glassfish.jersey.client.spi.PreInvocationInterceptor;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

public class PreInvocationUsageTest {
    static final String KEY = "thread_name";
    private static final String URL = "http://localhost:8080";

    @Test
    public void contextPropagatesFromCallerToExecutorInAsync() throws InterruptedException, ExecutionException, TimeoutException {
        String thisThread = Thread.currentThread().getName();
        Client client = ClientBuilder.newBuilder()
                .register(ContextPropagator.class)
                .register(new OtherThreadChecker(thisThread))
                .build();

        Future<Response> response = client.target(URL).request().async().get();
        assertEquals(200, response.get(1, TimeUnit.SECONDS).getStatus());

    }

    @Test
    public void contextPropagatesFromCallerToExecutorInRx() throws InterruptedException, ExecutionException, TimeoutException {
        String thisThread = Thread.currentThread().getName();
        Client client = ClientBuilder.newBuilder()
                .register(ContextPropagator.class)
                .register(new OtherThreadChecker(thisThread))
                .build();

        CompletionStage<Response> response = client.target(URL).request().rx().get();
        assertEquals(200, response.toCompletableFuture().get(1, TimeUnit.SECONDS).getStatus());

    }

    static class ContextPropagator implements PreInvocationInterceptor {

        @Override
        public void beforeRequest(ClientRequestContext requestContext) {
            requestContext.setProperty(KEY, Thread.currentThread().getName());
        }
    }

    static class OtherThreadChecker implements ClientRequestFilter {

        public OtherThreadChecker(String callingThread) {
            this.callingThread = callingThread;
        }

        private String callingThread;

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            String currentThread = Thread.currentThread().getName();
            System.out.println("Executing in thread " + currentThread);
            if (!currentThread.equals(callingThread)) {
                String context = (String) requestContext.getProperty(KEY);
                System.out.println("Tracing context is " + context);
                if (callingThread.equals(context)) {
                    requestContext.abortWith(Response.ok().build());
                } else {
                    requestContext.abortWith(Response.serverError().entity("unexpected request in context: " + context).build());
                }
            } else {
                requestContext.abortWith(Response.serverError().entity("Request executed at calling thread").build());
            }
        }
    }
}
