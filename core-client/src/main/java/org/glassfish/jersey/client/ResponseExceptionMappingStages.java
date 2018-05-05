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
package org.glassfish.jersey.client;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.model.internal.RankedComparator;
import org.glassfish.jersey.process.internal.AbstractChainableStage;
import org.glassfish.jersey.process.internal.ChainableStage;
import org.glassfish.jersey.process.internal.RequestScope;
import org.glassfish.jersey.process.internal.Stage;

class ResponseExceptionMappingStages {

    private ResponseExceptionMappingStages() {
        // Prevents instantiation
    }

    /**
     * Create client response exception mapper stage using the injection
     * manager. May return {@code null}.
     *
     * @param locator HK2 service locator to be used.
     * @return configured response exception mapper stage, or {@code null} in
     * case there are no
     * {@link ResponseExceptionMapper response exception mappers} registered in
     * the service locator.
     */
    static ChainableStage<ClientResponse> createResponseExceptionMappingStage(final ServiceLocator locator) {
        RankedComparator<ResponseExceptionMapper> comparator
                = new RankedComparator<ResponseExceptionMapper>(RankedComparator.Order.DESCENDING);
        Iterable<ResponseExceptionMapper> responseFilters
                = Providers.getAllProviders(locator, ResponseExceptionMapper.class, comparator);
        return responseFilters.iterator().hasNext() ? new ExceptionMapperStage(responseFilters) : null;
    }

    private static class ExceptionMapperStage extends AbstractChainableStage<ClientResponse> {

        private final Iterable<ResponseExceptionMapper> mappers;

        private ExceptionMapperStage(Iterable<ResponseExceptionMapper> mappers) {
            this.mappers = mappers;
        }

        @Override
        public Stage.Continuation<ClientResponse> apply(ClientResponse responseContext) {
                Map<ResponseExceptionMapper, Integer> mapperPriorityMap = new HashMap<ResponseExceptionMapper, Integer>();
                for (ResponseExceptionMapper mapper : mappers) {
                    if (mapper.handles(responseContext.getStatus(), responseContext.getHeaders())) {
                        mapperPriorityMap.put(mapper, mapper.getPriority());
                    }
                }
                if (mapperPriorityMap.size() > 0) {
                    Map<Throwable, Integer> errors = new HashMap<Throwable, Integer>();
                    ClientRequest clientRequest = responseContext.getRequestContext();
                    ClientRuntime runtime = clientRequest.getClientRuntime();
                    RequestScope requestScope = runtime.getRequestScope();

                    for (Map.Entry<ResponseExceptionMapper, Integer> mapperPriorityEntry : mapperPriorityMap.entrySet()) {
                        ResponseExceptionMapper mapper = mapperPriorityEntry.getKey();
                        Integer priority = mapperPriorityEntry.getValue();
                        Throwable throwable = mapper.toThrowable(new InboundJaxrsResponse(responseContext, requestScope));
                        if (throwable != null) {
                            errors.put(throwable, priority);
                        }
                    }

                    Throwable prioritised = null;
                    for (Map.Entry<Throwable, Integer> errorEntry : errors.entrySet()) {
                        if (prioritised == null || errorEntry.getValue() < errors.get(prioritised)) {
                            prioritised = errorEntry.getKey();
                        }
                    }

                    if (prioritised != null) {
                        throw (WebApplicationException) prioritised;
                    }
                }
            return Stage.Continuation.of(responseContext, getDefaultNext());
        }

    }

}