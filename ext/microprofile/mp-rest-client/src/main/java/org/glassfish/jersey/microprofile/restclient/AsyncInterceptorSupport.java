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

package org.glassfish.jersey.microprofile.restclient;

import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptor;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.glassfish.jersey.client.spi.PostInvocationInterceptor;
import org.glassfish.jersey.client.spi.PreInvocationInterceptor;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.core.Configurable;
import java.util.List;

class AsyncInterceptorSupport {
    private final List<AsyncInvocationInterceptorFactory> factories;
    private static final String KEY = AsyncInterceptorSupport.class.getName();

    AsyncInterceptorSupport(List<AsyncInvocationInterceptorFactory> factories) {
        this.factories = factories;
    }

    private PreInvocationInterceptor preSubmit() {
        return this::preSubmit;
    }

    private ClientRequestFilter preInvoke() {
        return this::preInvoke;
    }

    private PostInvocationInterceptor postInvoke() {
        return new PostInvoke();
    }

    private void preSubmit(ClientRequestContext requestContext) {
        AsyncInvocationInterceptor[] interceptors = factories.stream().map(AsyncInvocationInterceptorFactory::newInterceptor)
                .toArray(AsyncInvocationInterceptor[]::new);
        requestContext.setProperty(KEY, interceptors);
        //applyContext methods need to be called in reverse ordering of priority
        for (AsyncInvocationInterceptor interceptor : interceptors) {
            interceptor.prepareContext();
        }
    }

    private void preInvoke(ClientRequestContext requestContext) {
        AsyncInvocationInterceptor[] interceptors = readInterceptors(requestContext);
        if (interceptors != null) {
            for (int i = interceptors.length - 1; i >= 0; i--) {
                AsyncInvocationInterceptor interceptor = interceptors[i];
                interceptor.applyContext();
            }
        }
    }

    private AsyncInvocationInterceptor[] readInterceptors(ClientRequestContext requestContext) {
        Object interceptors = requestContext.getProperty(KEY);
        if (interceptors instanceof AsyncInvocationInterceptor[]) {
            return (AsyncInvocationInterceptor[]) interceptors;
        }
        return null;
    }

    static void register(List<AsyncInvocationInterceptorFactory> interceptorFactories,
                         Configurable<? extends Configurable> config) {
        if (interceptorFactories != null && !interceptorFactories.isEmpty()) {
            AsyncInterceptorSupport support = new AsyncInterceptorSupport(interceptorFactories);
            config.register(support.preSubmit()).register(support.preInvoke()).register(support.postInvoke());
        }
    }

    private class PostInvoke implements PostInvocationInterceptor {

        @Override
        public void afterRequest(ClientRequestContext requestContext, ClientResponseContext responseContext) {
            AsyncInvocationInterceptor[] interceptors = readInterceptors(requestContext);
            if (interceptors == null) {
                return;
            }
            for (AsyncInvocationInterceptor interceptor : interceptors) {
                interceptor.removeContext();
            }
        }

        @Override
        public void onException(ClientRequestContext requestContext, ExceptionContext exceptionContext) {
            AsyncInvocationInterceptor[] interceptors = readInterceptors(requestContext);
            if (interceptors == null) {
                return;
            }
            for (AsyncInvocationInterceptor interceptor : interceptors) {
                try {
                    interceptor.removeContext();
                } catch (Throwable t) {
                    exceptionContext.getThrowables().add(t);
                }
            }
        }


    }


}
