/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.container.util;

import org.openremote.model.Container;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.LogManager;

public class LogUtil {

    public static final String LOGGING_CONFIG_FILE = "LOGGING_CONFIG_FILE";

    protected LogUtil() {
    }

    /**
     * If system property <code>java.util.logging.config.file</code> has not been set, try to load the
     * logging configuration specified in environment variable <code>LOGGING_CONFIG_FILE</code> as a file.
     * If this wasn't set, try to find the file <code>/deployment/manager/logging.properties</code>.
     * If this also wasn't found, load the given default logging configuration from the classpath.
     * <p>
     * This method should be called in a <code>static { ... }</code> block in the "first" class of your
     * application (typically where your <code>main()</code> method is located).
     */
    public static void configureLogging(String defaultLoggingProperties) throws ExceptionInInitializerError {
        // If no JUL configuration is provided
        if (System.getProperty("java.util.logging.config.file") == null) {
            // Load the logging configuration file specified with an environment variable
            if (System.getenv(LOGGING_CONFIG_FILE) != null) {
                Path loggingConfigFile = Paths.get(System.getenv(LOGGING_CONFIG_FILE));
                if (!Files.isReadable(loggingConfigFile)) {
                    throw new ExceptionInInitializerError("LOGGING_CONFIG_FILE is not readable: " + loggingConfigFile.toAbsolutePath());
                }
                try (InputStream is = Files.newInputStream(loggingConfigFile)) {
                    System.out.println("Using logging configuration: " + loggingConfigFile.toAbsolutePath());
                    LogManager.getLogManager().readConfiguration(is);
                } catch (Exception ex) {
                    throw new ExceptionInInitializerError(ex);
                }
            } else {
                // Try to find /deployment/manager/logging.properties
                if (Files.isReadable(Paths.get("/deployment/manager/logging.properties"))) {
                    try (InputStream is = Files.newInputStream(Paths.get("/deployment/manager/logging.properties"))) {
                        System.out.println("Using logging configuration: /deployment/manager/logging.properties");
                        LogManager.getLogManager().readConfiguration(is);
                    } catch (Exception ex) {
                        throw new ExceptionInInitializerError(ex);
                    }
                } else {
                    // Or load a default configuration from the classpath
                    try (InputStream is = Container.class.getClassLoader().getResourceAsStream(defaultLoggingProperties)) {
                        if (is != null) {
                            System.out.println("Using logging configuration: logging.properties on classpath");
                            LogManager.getLogManager().readConfiguration(is);
                        }
                    } catch (Exception ex) {
                        throw new ExceptionInInitializerError(ex);
                    }
                }
            }
        }
    }
}
