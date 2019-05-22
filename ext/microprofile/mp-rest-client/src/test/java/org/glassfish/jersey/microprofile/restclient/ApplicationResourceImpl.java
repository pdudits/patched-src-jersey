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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author David Kral
 * @author Patrik Dudits
 */
public class ApplicationResourceImpl implements ApplicationResource {
    @Override
    public String getValue() {
        return "This is default value!";
    }

    @Override
    public String postAppendValue(String value) {
        return null;
    }

    @Override
    public List<String> list(int size) {
        return IntStream.rangeClosed(1, size).mapToObj(i -> "int" + i).collect(Collectors.toList());
    }

    @Override
    public Map<String, Integer> map(int size) {
        return IntStream.rangeClosed(1, size).mapToObj(i -> i).collect(Collectors.toMap(String::valueOf, Function.identity()));
    }

    @Override
    public Map<String, Integer> acceptList(List<Integer> numbers) {
        Map<String, Integer> result = new HashMap<>();
        result.put("size", numbers != null ? numbers.size() : -1);
        return result;
    }
}
