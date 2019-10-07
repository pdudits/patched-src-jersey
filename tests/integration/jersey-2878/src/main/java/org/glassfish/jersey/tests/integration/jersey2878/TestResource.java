/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.tests.integration.jersey2878;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * A simple resource that returns a string.
 *
 * @author Stepan Vavra
 */
@Path("string")
public class TestResource {

    @GET
    public String string() {
        return "Hello, World! "
                + "In production this response would be large, "
                + "and unsuitable for buffering in memory";
    }
}
