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

package com.lafaspot.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Caches values for the host machine's IP address and host name. This class is preferred over direct calls to {@link java.net.InetAddress}, to
 * improve performance: the IP address and host name are used many times in a typical application, and getting them from the JDK is not
 * cheap, due to synchronized lookups and calls to the configured name service.
 *
 * <p>
 * hosts are placed in service, and removed, by modifying their network settings, but by convention, a host's IP properties (name and address)
 * are assumed not to change during the lifetime of the application. Some early requests to {@link java.net.InetAddress} may fail, but once the
 * network is properly set up and a valid name and address have been cached, it is agreed that if a host's IP properties have to change, the
 * common applications it is running will have to be restarted.
 *
 * <p>
 * Accordingly, when this class is loaded, and its singleton value initialized, it tries to get the local host address and name. If successful, it
 * caches them forever. If unsuccessful, no exception is thrown; every call to this cache will result in another call to {@link java.net.InetAddress},
 * until eventually one succeeds and the values are cached.
 *
 * <p>
 * When a host has more than one IP address, the one that will be returned by this class's methods is implementation-dependent.
 *
 * <p>
 * All methods in this class are multithread-safe.
 */
public enum InetAddressCache {

    /** The singleton value. */
    INSTANCE;

    /**
     * Returns an IP address for the local host. If the cache value is set, it is returned; otherwise,
     * java.net.InetAddress.getLocalHost is called, and
     * its return value cached for subsequent calls.
     *
     * @return the cached IP address for the local host machine
     * @throws UnknownHostException if the address cannot be obtained
     * @throws SecurityException if the client does not have permission to look up the address
     * @see InetAddress#getLocalHost()
     */
    public InetAddress getLocalHost() throws UnknownHostException {
        return initLocalHostAddress();
    }

    /**
     * Returns a name for the local host. If the cache value is set, it is returned; otherwise
     * java.net.InetAddress.getHostName is called on the cached
     * local host address, and that value is cached for subsequent calls.
     *
     * <p>
     * The method java.net.InetAddress.getHostName does cache its result, but caching it here makes it possible to remove all direct calls to
     * {@link java.net.InetAddress} from client code, which will make it easier to spot potential performance problems just by looking for imports.
     *
     * @return the cached name for the local host machine
     * @throws UnknownHostException if a name or address cannot be obtained
     * @throws SecurityException if the client does not have permission to look up the name or address
     * @see InetAddress#getHostName()
     */
    public String getHostName() throws UnknownHostException {
        return initLocalHostName();
    }

    /**
     * Checks for a cached address, and gets one from the JDK if needed, caching it for subsequent calls.
     *
     * @return the IP address originally obtained from the JDK
     * @throws UnknownHostException if the address lookup fails
     * @throws SecurityException if the client does not have permission to look up the address
     */
    private InetAddress initLocalHostAddress() throws UnknownHostException {
        /*
         * This logic is MT-safe, but not perfectly efficient: multiple Threads will "see" a null cache value and call the JDK class; all of them will
         * set the cache value. The reads and writes are atomic, because the variable is volatile, and calls to this method after the first cache
         * write will "see" a valid reference, and stop calling the JDK. This approach incurs some extra JDK calls in the early going, but avoids
         * synchronization cost forever after.
         */
        if (localHostAddress == null) {
            try {
                localHostAddress = InetAddress.getLocalHost();
            } catch (final UnknownHostException ex) {
                localHostAddress = InetAddress.getLoopbackAddress();
            }
        }
        return localHostAddress;
    }

    /** The cached IP address for the local host machine. */
    private volatile InetAddress localHostAddress;

    /**
     * Checks for a cached host name, and gets one from the JDK if needed, caching it for subsequent calls.
     *
     * @return the host name originally obtained from the JDK
     * @throws UnknownHostException if the address lookup fails
     * @throws SecurityException if the client does not have permission to look up the name or address
     */
    private String initLocalHostName() throws UnknownHostException {
        /*
         * See the comment in initLocalHostAddress.
         */
        if (localHostName == null) {
            localHostName = initLocalHostAddress().getHostName();
        }
        return localHostName;
    }

    /** The cached DNS name for the local host machine. */
    private volatile String localHostName;

    /**
     * Calls {@link InetAddress.getLocalHost} to get the host machine's IP address, and then calls {@link InetAddress.getHostName} to get a DNS name
     * for the host, and caches those values.
     *
     * <p>
     * If the name and address cannot be obtained because of an {@link UnknownHostException}, this method silently discards the exception, assuming it
     * is the result of a transient problem. This leaves the cache values unset; subsequent calls will retry the JDK requests until successful.
     *
     * <p>
     * A {@link SecurityException} is more serious, representing a configuration error that needs to be fixed by human intervention. If this is
     * thrown, it should be allowed to propagate to the top of the application stack.
     *
     * @throws SecurityException if the client does not have permission to look up the host's name or address
     */
    InetAddressCache() {
        try {
            initLocalHostAddress();
            initLocalHostName();
        } catch (final UnknownHostException e) { /* leave cache unset */
        }
    }

}
