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
package org.jkiss.dbeaver;

public class HttpException extends DBException {
    private final Integer statusCode;
    private final String body;

    public HttpException(String message) {
        super(message);
        this.statusCode = null;
        this.body = null;
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.body = null;
    }

    public HttpException(String message, int statusCode, String body) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public HttpException(String message, Throwable cause, int statusCode, String body) {
        super(message, cause);
        this.statusCode = statusCode;
        this.body = body;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }
}
