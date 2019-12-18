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

import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * This class is used to derive top-level domain, hostname, colo, ip address.
 *
 */
public enum SystemSettings {
    /**
     * Instance.
     */
    INSTANCE;

    protected String hostname;
    private final Logger logger = LoggerFactory.getLogger(SystemSettings.class);
    protected String colo;
    protected String ip;

    /**
     * top-level domain is domain at highest level in the hierarchical Domain Name System. for e.g: domain name www.example.com, the top-level domain
     * is .com
     */
    protected String tld;

    /**
     * @return the tld top-level domain
     */
    public String getTld() {
        return tld;
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @return the colo
     */
    public String getColo() {
        return colo;
    }

    /**
     * @return the ip
     */
    public String getIp() {
        return ip;
    }

    SystemSettings() {
        try {
            hostname = InetAddressCache.INSTANCE.getHostName();
            final int tldStartIndex = hostname.lastIndexOf(".");
            if (tldStartIndex > 0 && hostname.length() > tldStartIndex) {
                tld = hostname.substring(tldStartIndex + 1);
            } else {
                tld = "undef";
            }
        } catch (final UnknownHostException e) {
            hostname = "localhost";
            tld = "undef";
            final Marker fatal = MarkerFactory.getMarker("FATAL");
            logger.error(fatal,
                    "Could not determine localhost hostname, colo, cluster, hostname values are initialized to undef,undef,localhost,127.0.0.1.");
        }

        try {
            ip = InetAddressCache.INSTANCE.getLocalHost().getHostAddress();
        } catch (final UnknownHostException e) {
            ip = "127.0.0.1";
        }

        final String[] tokens = hostname.split("\\.");
        if (tokens == null || tokens.length < 3) {
            colo = "undef";
            return;
        }

        colo = tokens[2].trim();
    }
}
