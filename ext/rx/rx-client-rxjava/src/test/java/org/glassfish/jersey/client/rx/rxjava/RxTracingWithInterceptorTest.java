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

package org.glassfish.jersey.client.rx.rxjava;

import org.glassfish.jersey.client.spi.PreInvocationInterceptor;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;
import org.glassfish.jersey.process.JerseyProcessingUncaughtExceptionHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RxTracingWithInterceptorTest {
    static final String KEY = "thread_name";
    static final String EXECUTING_THREAD = Thread.currentThread().getName();

    private Client client;

    @Before
    public void setUp() throws Exception {
        client = ClientBuilder.newClient().register(TerminalClientRequestFilter.class);
        client.register(RxObservableInvokerProvider.class)
                .register(ContextPropagator.class)
                .register(new OtherThreadChecker(EXECUTING_THREAD))
                .register(TerminalClientRequestFilter.class)
                .register(RxObservableInvokerProvider.class);
    }

    @After
    public void tearDown() throws Exception {
        client.close();
        client = null;
    }

    @Test
    public void testReadEntityViaClass() throws Throwable {
        final String response = client.target("http://jersey.java.net")
                .request()
                .rx(RxObservableInvoker.class)
                .get(String.class)
                .toBlocking()
                .toFuture()
                .get();

        assertThat(response, is("NO-ENTITY"));
    }

    static class ContextPropagator implements PreInvocationInterceptor {

        @Override
        public void beforeRequest(ClientRequestContext requestContext) {
            System.out.println("Setting context in thead " + Thread.currentThread().getName());
            requestContext.setProperty(KEY, Thread.currentThread().getName());
        }
    }

    static class OtherThreadChecker implements ClientRequestFilter {

        private String callingThread;

        public OtherThreadChecker(String callingThread) {
            this.callingThread = callingThread;
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            String currentThread = Thread.currentThread().getName();
            System.out.println("Executing in thread " + currentThread);
            if (!currentThread.equals(callingThread)) {
                String context = (String) requestContext.getProperty(KEY);
                System.out.println("Tracing context is " + context);
                if (!callingThread.equals(context)) {
                    requestContext.abortWith(Response.serverError().entity("unexpected request in context: " + context).build());
                }
            } else {
                requestContext.abortWith(Response.serverError().entity("Request executed at calling thread").build());
            }
        }
    }

}
