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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test class for AnnotationClassScanner.
 *
 * @author sundarms
 */
public class AnnotationClassScannerTest {
	/** accept package for testing. */
	private final List<String> acceptPackages = Collections.singletonList("com.lafaspot.common.util");
	/**
	 * Test constructor
	 */
	@Test
	public void testConstructor() {
		AnnotationClassScanner annotationScanner = new AnnotationClassScanner(acceptPackages);
		Set<Class<?>> classes = annotationScanner.scan(DummyAnnotation.class);
		Assert.assertTrue(classes.size() > 0, "Should find at least one class with DummyScanAnnotation");
	}

	/**
	 * Test constructor with ClassLoader
	 */
	@Test
	public void testConstructorWithClassLoader() {
		AnnotationClassScanner annotationScanner = new AnnotationClassScanner(acceptPackages);
		AnnotationClassScanner annotationScanner1 = new AnnotationClassScanner(acceptPackages, this.getClass().getClassLoader());
		Set<Class<?>> classes = annotationScanner.scan(Deprecated.class);
		Set<Class<?>> classes1 = annotationScanner1.scan(Deprecated.class);
		Assert.assertEquals(classes, classes1, "Annotated classes found in both scanner should be same");
	}

	/**
	 * Test scan for class which exist with annotation
	 */
	@Test
	public void testScanWithAnnotations() {
		AnnotationClassScanner annotationScanner = new AnnotationClassScanner(acceptPackages);
		Set<Class<?>> classes = annotationScanner.scan(DummyAnnotation.class);
		Assert.assertEquals(classes.size(), 1, "Should return one class with DummyScanAnnotation");
	}

	/**
	 * Test scan for class which exist without annotation
	 */
	@Test
	public void testScanWithoutAnnotations() {
		AnnotationClassScanner annotationScanner = new AnnotationClassScanner(acceptPackages);
		Set<Class<?>> classes = annotationScanner.scan(DummyNoBeanAnnotation.class);
		Assert.assertEquals(classes.size(), 0, "Should return zero class with DummyScanAnnotationNoBeans");
	}

	/**
	 * Test scan for classes by rejecting some package
	 */
	@Test
	public void testScanRejecting() {
		final List<String> acceptPackages = Collections.emptyList();
		AnnotationClassScanner annotationScanner = new AnnotationClassScanner(acceptPackages);
		String[] rejectPackages = { "com.lafaspot.common.util" };
		Set<Class<?>> classes = annotationScanner.scan(DummyAnnotation.class, rejectPackages);
		Assert.assertEquals(classes.size(), 0, "Should return zero class with DummyScanAnnotation");
	}
}
