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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.lafaspot.logfast.logging.LogManager;

/**
 * Annotation class scanner which scans for all annotations based on the
 * annotation class passed in.
 * 
 *
 * @param <T>
 *            Annotation Class
 */
public class AnnotationClassScanner<T extends Annotation> {

	/**
	 * Class to be annotated.
	 */
	private final Class<T> annotationClazz;
	/**
	 * Filtering out classes not needed.
	 */
	private final Set<String> excludeFilter;
	/**
	 * Specifying filters to be included.
	 */
	private final Set<String> allowFilter = new HashSet<String>();
	/**
	 * Set of all the resource paths which will be scanned. This set is build as
	 * and will block scanning the same path again which avoids recursion.
	 */
	private final Set<String> dirLookupSet = new HashSet<String>();
	/**
	 * LogManager class.
	 */
	private final LogManager logManager = new LogManager();

	/**
	 * Private annotationClassScanner constructor.
	 * 
	 * @param annotationClazz
	 *            Annotated class
	 */
	private AnnotationClassScanner(final Class<T> annotationClazz) {
		if (annotationClazz == null) {
			throw new IllegalArgumentException("Annotation class can't be null");
		}
		this.annotationClazz = annotationClazz;
		final String[] packageToExclude = { "java.", "javax.", "org.ietf.jgss", "org.omg.", "org.w3c.dom.",
				"org.xml.sax.", "sun.tools.", "sun.jvmstat.", "com.sun.", "org.junit.", "org.testng.", "bsh.",
				"org.relaxng.", "mockit.", "com.beust.", "org.apache.log4j.", "ch.qos.logback.", "org.slf4j.",
				"org.apache.commons.logging." };
		excludeFilter = new HashSet<String>(Arrays.asList(packageToExclude));
	}

	/**
	 * AnnotationClassScanner constructor.
	 * 
	 * @param annotationClazz
	 *            Annotated class
	 * @param allowFilter
	 *            Filter to be included
	 */
	public AnnotationClassScanner(final Class<T> annotationClazz, final List<String> allowFilter) {
		this(annotationClazz);
		if (allowFilter == null) {
			throw new IllegalArgumentException("Include filter can't be null");
		}
		this.allowFilter.addAll(allowFilter);
	}

	/**
	 * Scanning the annotated classes.
	 * 
	 * @return Set of annotated classes
	 */
	public Set<Class<?>> scanAnnotatedClasses() {
		final Set<Class<?>> clazzez = new HashSet<Class<?>>();
		clazzez.addAll(scanPackagesAnnotatedClasses());
		clazzez.addAll(scanURLClassLoaderAnnotatedClasses());
		return clazzez;
	}

	/**
	 * Scanning packages annotated classes.
	 * 
	 * @return Set of annotated classes
	 */
	private Set<Class<?>> scanPackagesAnnotatedClasses() {
		final Set<Class<?>> clazzez = new HashSet<Class<?>>();
		final Package[] packages = Package.getPackages();
		for (final Package sPackage : packages) {
			try {
				clazzez.addAll(scanPackageAnnotatedClasses(sPackage));
			} catch (final IOException e) {
				logManager.getLogger(new AnnotationScannerContext(sPackage.getName())).warn("AnnotationClassScanner failed for package", e);
			}
		}
		return clazzez;
	}

	/**
	 * Scanning url classloader annotated classes.
	 * 
	 * @return Set of annotated classes.
	 */
	private Set<Class<?>> scanURLClassLoaderAnnotatedClasses() {
		final ClassLoader classLoader = this.getClass().getClassLoader();
		if (!(classLoader instanceof URLClassLoader)) {
			throw new IllegalArgumentException("Classloader is not a URL classloader");
		}
		final URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
		final URL[] urls = urlClassLoader.getURLs();
		final Set<Class<?>> clazzez = new HashSet<Class<?>>();
		for (final URL url : urls) {
			try {
				clazzez.addAll(scanURLAnnotatedClasses(url, ""));
			} catch (final IOException e) {
				// ignore and continue to loop.
			}
		}
		return clazzez;
	}

	/**
	 * Scanning package annotated classes.
	 * 
	 * @param sPackage
	 *            Java package
	 * @return Set of annotated classes
	 * @throws IOException
	 *             Exceptions during operation.
	 */
	private Set<Class<?>> scanPackageAnnotatedClasses(final Package sPackage) throws IOException {
		final String packagePath = sPackage.getName().replace('.', '/');
		final Enumeration<URL> packageResources = Thread.currentThread().getContextClassLoader()
				.getResources(packagePath);
		final Set<Class<?>> clazzez = new HashSet<Class<?>>();
		while (packageResources.hasMoreElements()) {
			clazzez.addAll(scanURLAnnotatedClasses(packageResources.nextElement(), sPackage.getName()));
		}
		return clazzez;
	}

	/**
	 * Scanning URL annotated classes.
	 * 
	 * @param url
	 *            URL
	 * @param packageName
	 *            Name of the package
	 * @return Set of annotated classes
	 * @throws IOException
	 *             Exception during operation
	 */
	private Set<Class<?>> scanURLAnnotatedClasses(final URL url, final String packageName) throws IOException {
		final Set<Class<?>> clazzez = new HashSet<Class<?>>();
		String urlPath = url.toExternalForm();
		if (urlPath.contains("!") && urlPath.startsWith("jar:file:")) {
			String[] split = urlPath.split("!");
			split = split[0].split(":");
			clazzez.addAll(scanJarAnnotatedClasses(new JarFile(split[2])));
			return clazzez;
		} else if (urlPath.startsWith("file:")) {
			urlPath = urlPath.substring(5);
			final File directory = new File(urlPath);
			if (!directory.exists()) {
				return clazzez;
			}
			if (directory.isFile() && directory.getPath().endsWith(".jar")) {
				clazzez.addAll(scanJarAnnotatedClasses(directory));
				return clazzez;
			}
			final File[] files = directory.listFiles();
			if (files == null) {
				return clazzez;
			}
			for (final File file : files) {
				if (file.isDirectory()) {
					if (!dirLookupSet.contains(file.getCanonicalPath())) {
						dirLookupSet.add(file.getCanonicalPath());
						String prefix = packageName + ".";
						if (packageName.isEmpty()) {
							prefix = "";
						}
						clazzez.addAll(scanURLAnnotatedClasses(new URL("file:" + file.getAbsolutePath()),
								prefix + file.getName()));
					}
				} else if (file.getName().endsWith(".class")) {
					final String className = packageName + '.' + file.getName();
					final Class<?> clazz = applyFilter(className.substring(0, className.length() - 6));
					if (clazz != null) {
						clazzez.add(clazz);
					}
				}
			}
			return clazzez;
		} else {
			throw new InvalidPathException(urlPath, "The path is not found");
		}
	}

	/**
	 * Scanning jar annotated classes.
	 * 
	 * @param path
	 *            Jar file path
	 * @return Set of annotated classes
	 * @throws IOException
	 *             Exception during operation
	 */
	public Set<Class<?>> scanJarAnnotatedClasses(final File path) throws IOException {
		return scanJarAnnotatedClasses(new JarFile(path));
	}

	/**
	 * Scanning jar annotated classes.
	 * 
	 * @param jar
	 *            Jar file
	 * @return Set of annotated classes
	 * @throws IOException
	 *             Exception during operation
	 */
	public Set<Class<?>> scanJarAnnotatedClasses(final JarFile jar) throws IOException {
		final Set<Class<?>> clazzez = new HashSet<Class<?>>();
		final Enumeration<JarEntry> it = jar.entries();
		while (it.hasMoreElements()) {
			final JarEntry jarEntry = it.nextElement();
			if (jarEntry.getName().endsWith(".class")) {
				final String className = jarEntry.getName().replaceAll("/", "\\.");
				final Class<?> clazz = applyFilter(className.substring(0, className.length() - 6));
				if (clazz != null) {
					clazzez.add(clazz);
				}
			}
		}
		return clazzez;
	}

	/**
	 * Helper function to apply the filter.
	 * 
	 * @param clazzName
	 *            Class name
	 * @return Annotated class
	 */
	private Class<?> applyFilter(final String clazzName) {
		if (isAllowed(clazzName)) {
			try {
				final Class<?> clazz = Class.forName(clazzName, false, this.getClass().getClassLoader());
				final T annotation = clazz.getAnnotation(annotationClazz);
				if (annotation != null) {
					return clazz;
				}
			} catch (final ClassNotFoundException | NoClassDefFoundError | UnsatisfiedLinkError
					| UnsupportedClassVersionError e) {
				logManager.getLogger(new AnnotationScannerContext(clazzName)).debug("Unable to search classes for annotations", e);
			}
		}
		return null;
	}

	/**
	 * Helper function to verify whether clazzName is in allowFilter or not.
	 * 
	 * @param clazzName
	 *            Name of the class
	 * @return true if clazzName is in the allowFilter
	 */
	private boolean isAllowed(final String clazzName) {
		if (!allowFilter.isEmpty()) {
			for (final String allowPrefix : allowFilter) {
				if (clazzName.startsWith(allowPrefix)) {
					return true;
				}
			}
			return false;
		}
		final String[] tokens = clazzName.split("\\.");
		String path = "";
		for (final String token : tokens) {
			path += token + ".";
			if (excludeFilter.contains(path)) {
				return false;
			}
		}
		return true;
	}

}
