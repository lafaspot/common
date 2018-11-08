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

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Encapsulates the config paramaters that the worker executor service can use.
 *
 * @author nitu
 *
 */
@ThreadSafe
public final class WorkerConfig {

    /** config to enable thread local cleanup on exit. */
    private boolean enableThreadLocalCleanupOnExit = false;
    /** config to enable thread local cleanup periodically. */
    private boolean enableThreadLocalCleanupPeriodically = false;

    /**
     * Constructor the worker config class.
     *
     * @param builder The builder that contains the config values set by the client.
     */
    private WorkerConfig(final WorkerConfig.Builder builder) {
        this.enableThreadLocalCleanupOnExit = builder.enableThreadLocalCleanupOnExit;
        this.enableThreadLocalCleanupPeriodically = builder.enableThreadLocalCleanupPeriodically;

    }

    /**
     * @return true if the config is enabled to cleanup the thread locals on exit.
     */
    public boolean getEnableThreadLocalCleanupOnExit() {
        return enableThreadLocalCleanupOnExit;
    }

    /**
     * @return true if the config is enabled to cleanup the thread locals periodically.
     */
    public boolean getEnableThreadLocalCleanupPeriodically() {
        return enableThreadLocalCleanupPeriodically;
    }

    /**
     * Builder class for the config class.
     *
     * @author nitu
     *
     */
    @NotThreadSafe
    public static class Builder {

        /** config to enable thread local cleanup on exit. */
        private boolean enableThreadLocalCleanupOnExit = false;
        /** config to enable thread local cleanup periodically. */
        private boolean enableThreadLocalCleanupPeriodically = false;

        /**
         * set the config to enable thread local cleanup on exit.
         *
         * @param enable set to true to enable config.
         * @return the builder
         */
        public Builder setEnableThreadLocalCleanupOnExit(final boolean enable) {
            this.enableThreadLocalCleanupOnExit = enable;
            return this;
        }

        /**
         * set the config to enable thread local cleanup periodically.
         *
         * @param enable set to true to enable config.
         * @return the builder
         */
        public Builder setEnableThreadLocalCleanupPeriodically(final boolean enable) {
            this.enableThreadLocalCleanupPeriodically = enable;
            return this;
        }

        /**
         * build the config object.
         *
         * @return the config object.
         */
        public WorkerConfig build() {
            return new WorkerConfig(this);
        }
    }
}
