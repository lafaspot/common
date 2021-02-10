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

package com.lafaspot.common.util;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/**
 * Annotation scanner class, wrapper around ClassGraph class.
 *
 * @author sundarms
 */
public class AnnotationClassScanner {
    /** Overridden ClassLoader to be used for scanning classes. */
    private ClassLoader classLoader;

    /** ClassGraph object used for scanning. */
    private ClassGraph classGraph;

    /** List of packages we dont want to scan for annotation. */
    private static final String[] DEFAULT_REJECT_PACKAGES = { "java", "javafx", "com.sun", "sun.tools", "org.testng", "com.beust.testng",
            "org.mockito", "ch.qos.logback", "org.slf4j", "org.apache", "io.netty", "com.google" };

    /** List of packages we want to scan for annotation. */
    private String[] acceptPackages;

    /** Logger. */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Constructor.
     *
     * @param acceptPackages List of packages to scan for
     */
    public AnnotationClassScanner(final List<String> acceptPackages) {
        this.acceptPackages = acceptPackages.toArray(new String[0]);
        this.classLoader = null;
        this.classGraph = new ClassGraph();
    }

    /**
     * Constructor.
     *
     * @param acceptPackages List of packages to scan for
     * @param classLoader ClassLoader to be overridden
     */
    public AnnotationClassScanner(final List<String> acceptPackages, final ClassLoader classLoader) {
        this.acceptPackages = acceptPackages.toArray(new String[0]);
        this.classLoader = classLoader;
        this.classGraph = new ClassGraph();
    }

    /**
     * Scan for annotated classes N.B. It excludes libs/ext jars and uses default rejected packages {@link #DEFAULT_REJECT_PACKAGES}
     *
     * @param <T> generic
     * @param annotationClass AnnotationClass to be scanned for
     * @return Set of class annotated with provided annotationClass
     */
    public <T extends Annotation> Set<Class<?>> scan(@Nonnull final Class<T> annotationClass) {
        return scan(annotationClass, DEFAULT_REJECT_PACKAGES, true /* exclude libs/ext jars */);
    }

    /**
     * Scan for annotated classes, including acceptPackages and excluding rejectPackages N.B. It excludes libs/ext jars
     *
     * @param <T> generic
     * @param annotationClass AnnotationClass to be scanned for
     * @param rejectPackages List of packages to be excluded from scanning
     * @return Set of class annotated with provided annotationClass
     */
    public <T extends Annotation> Set<Class<?>> scan(@Nonnull final Class<T> annotationClass, @Nonnull final String[] rejectPackages) {
        return scan(annotationClass, rejectPackages, true /* exclude libs/ext jars */);
    }

    /**
     * Scan for annotated classes, including acceptPackages and excluding rejectPackages.
     *
     * @param <T> generic
     * @param annotationClass AnnotationClass to be scanned for
     * @param rejectPackages List of packages to be excluded from scanning
     * @param excludeLibsOrExtJars Whether to exclude system lib/ext jars
     * @return Set of class annotated with provided annotationClass
     */
    public <T extends Annotation> Set<Class<?>> scan(@Nonnull final Class<T> annotationClass, @Nonnull final String[] rejectPackages,
                                                     final boolean excludeLibsOrExtJars) {
        final Set<Class<?>> classes = new HashSet<>();
        ScanResult scanResult = null;
        try {
            classGraph.enableAnnotationInfo()
                    .ignoreClassVisibility()
                    .acceptPackages(acceptPackages)
                    .rejectPackages(rejectPackages)
                    .removeTemporaryFilesAfterScan();
            if (excludeLibsOrExtJars) {
                classGraph.rejectLibOrExtJars();
            }
            if (classLoader != null) {
                classGraph.addClassLoader(classLoader);
            }
            scanResult = classGraph.scan();
            final ClassInfoList classInfoList = scanResult.getClassesWithAnnotation(annotationClass.getName());
            List<Class<?>> clazzes = classInfoList.loadClasses(true /* Ignore exceptions */);
            if (logger.isDebugEnabled()) {
                logger.debug("Scanned class size {} loaded class size {}", classInfoList.size(), clazzes.size());
            }
            classes.addAll(clazzes);
        } finally {
            if (scanResult != null) {
                scanResult.close();
            }
        }
        return classes;
    }
}
