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
import org.glassfish.jersey.client.spi.InvocationBuilderListener;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Configurable;
import java.util.Collections;
import java.util.List;

class AsyncInterceptorSupport {
    private final List<AsyncInvocationInterceptorFactory> factories;
    private static final String KEY = AsyncInterceptorSupport.class.getName();
    private static final AsyncInvocationInterceptor[] EMPTY = new AsyncInvocationInterceptor[0];
    private static final AsyncInterceptorSupport NOOP = new AsyncInterceptorSupport(Collections.emptyList());
    private AsyncInvocationInterceptor[] interceptors = EMPTY;

    AsyncInterceptorSupport(List<AsyncInvocationInterceptorFactory> factories) {
        this.factories = factories;
    }



    private InvocationBuilderListener preSubmit() {
        return this::preSubmit;
    }

    private ClientRequestFilter preInvoke() {
        return this::preInvoke;
    }

    private void preSubmit(InvocationBuilderListener.InvocationBuilderContext requestContext) {
        interceptors = factories.stream().map(AsyncInvocationInterceptorFactory::newInterceptor)
                .toArray(AsyncInvocationInterceptor[]::new);
        requestContext.property(KEY, interceptors);
        for (AsyncInvocationInterceptor interceptor : interceptors) {
            interceptor.prepareContext();
        }
    }

    private void preInvoke(ClientRequestContext requestContext) {
        //applyContext methods need to be called in rever    se ordering of priority
        for (int i = interceptors.length - 1; i >= 0; i--) {
            AsyncInvocationInterceptor interceptor = interceptors[i];
            interceptor.applyContext();
        }
    }

    void postInvoke() throws Throwable {
        Throwable fault = postInvokeOnException(null);
        if (fault != null) {
            throw fault;
        }
    }

    Throwable postInvokeOnException(Throwable fault) {
        for (AsyncInvocationInterceptor interceptor : interceptors) {
            try {
                interceptor.removeContext();
            } catch (Throwable t) {
                if (fault == null) {
                    fault = t;
                } else {
                    t.addSuppressed(t);
                }
            }
        }
        interceptors = EMPTY;
        return fault;
    }

    static AsyncInterceptorSupport register(boolean enabled, List<AsyncInvocationInterceptorFactory> interceptorFactories,
                                            Configurable<? extends Configurable> config) {
        if (enabled && interceptorFactories != null && !interceptorFactories.isEmpty()) {
            AsyncInterceptorSupport support = new AsyncInterceptorSupport(interceptorFactories);
            config.register(support.preSubmit()).register(support.preInvoke());
            return support;
        }
        return NOOP;
    }

}
