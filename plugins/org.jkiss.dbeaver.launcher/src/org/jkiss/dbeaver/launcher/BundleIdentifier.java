/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.launcher;

import java.util.StringTokenizer;

/**
 * A structured form for a version identifier.
 *
 * @see "http://www.oracle.com/technetwork/java/javase/versioning-naming-139433.html for information on valid version strings"
 * @see "http://openjdk.java.net/jeps/223 for information on the JavaSE-9 version JEP 223"
 */
class BundleIdentifier {
    private static final String DELIM = ". _-"; //$NON-NLS-1$
    private int major, minor, service;

    BundleIdentifier(int major, int minor, int service) {
        super();
        this.major = major;
        this.minor = minor;
        this.service = service;
    }

    /**
     * @throws NumberFormatException if cannot parse the major and minor version components
     */
    BundleIdentifier(String versionString) {
        super();
        StringTokenizer tokenizer = new StringTokenizer(versionString, DELIM);

        // major
        if (tokenizer.hasMoreTokens())
            major = Integer.parseInt(tokenizer.nextToken());

        try {
            // minor
            if (tokenizer.hasMoreTokens())
                minor = Integer.parseInt(tokenizer.nextToken());

            // service
            if (tokenizer.hasMoreTokens())
                service = Integer.parseInt(tokenizer.nextToken());
        } catch (NumberFormatException nfe) {
            // ignore the minor and service qualifiers in that case and default to 0
            // this will allow us to tolerate other non-conventional version numbers
        }
    }

    /**
     * Returns true if this id is considered to be greater than or equal to the given baseline.
     * e.g.
     * 1.2.9 >= 1.3.1 -> false
     * 1.3.0 >= 1.3.1 -> false
     * 1.3.1 >= 1.3.1 -> true
     * 1.3.2 >= 1.3.1 -> true
     * 2.0.0 >= 1.3.1 -> true
     */
    boolean isGreaterEqualTo(BundleIdentifier minimum) {
        if (major < minimum.major)
            return false;
        if (major > minimum.major)
            return true;
        // major numbers are equivalent so check minor
        if (minor < minimum.minor)
            return false;
        if (minor > minimum.minor)
            return true;
        // minor numbers are equivalent so check service
        return service >= minimum.service;
    }
}
