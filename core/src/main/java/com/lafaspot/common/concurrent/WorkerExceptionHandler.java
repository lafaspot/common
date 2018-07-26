/*
 * Copyright [yyyy] [name of copyright owner]
 * 
 * ====================================================================
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
 *  ====================================================================
 */
package com.lafaspot.common.concurrent;

/**
 * Worker exception handler.
 *
 * @param <E> exception type.
 */
public interface WorkerExceptionHandler<E extends Throwable> {

    /**
     * This method is called if a Worker exits with true and has an exception. Throwing a runtime exception results in a
     * will corrupt the state of the WorkerExecuter.
     *
     * @param exception the exception to map to a response.
     */
    void failed(E exception);
}
