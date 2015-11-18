/*
 * Copyright 2015, Yahoo Inc.
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Basic unit test for AnnotationClassScanner.
 *
 */
public class AnnotationClassScannerTest {
	/**
	 * Test positive case for annotation scanner with dummy annotation.
	 */
	@Test
	public void testAnnotationClassScanner() {
		final AnnotationClassScanner<DummyAnnotation> annotationScanner = new AnnotationClassScanner<DummyAnnotation>(
				DummyAnnotation.class, Arrays.asList("com.lafaspot.common.util"));
		final Set<Class<?>> beans = annotationScanner.scanAnnotatedClasses();
		Assert.assertEquals(1, beans.size());
		final Iterator it = beans.iterator();
		while (it.hasNext()) {
			Assert.assertEquals("class com.lafaspot.common.util.DummyBean", it.next().toString());
		}
	}

	/**
	 * Test annotation scanner with no beans for the annotation.
	 */
	@Test
	public void testScanClassPathNoBeans() {
		final AnnotationClassScanner<DummyNoBeanAnnotation> annotationScanner = new AnnotationClassScanner<DummyNoBeanAnnotation>(
				DummyNoBeanAnnotation.class, Arrays.asList("com.lafaspot.common.util"));
		final Set<Class<?>> beans = annotationScanner.scanAnnotatedClasses();
		Assert.assertEquals(0, beans.size());
	}

	/**
	 * Test annotation scanner with filter path that contains no annotation.
	 */
	@Test
	public void testScanClassPathBadPath() {
		final AnnotationClassScanner<DummyAnnotation> annotationScanner = new AnnotationClassScanner<DummyAnnotation>(
				DummyAnnotation.class, Arrays.asList("test.path"));
		final Set<Class<?>> beans = annotationScanner.scanAnnotatedClasses();
		Assert.assertEquals(0, beans.size());
	}
}
