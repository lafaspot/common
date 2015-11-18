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

import javax.xml.bind.annotation.XmlElement;

/**
 * Dummy Bean.
 */
@DummyAnnotation
public class DummyBean {
	/**
	 * Address
	 */
	private String testAddress;
	/**
	 * Notes
	 */
	private String testNotes;

	/**
	 * Get address.
	 * 
	 * @return Address
	 */
	@XmlElement(required = false)
	public String getTestAddress() {
		return testAddress;
	}

	/**
	 * Get test notes.
	 * 
	 * @return test notes
	 */
	@XmlElement(name = "notes", required = false)
	public String getTestNotes() {
		return testNotes;
	}

	/**
	 * Set address.
	 * 
	 * @param testAddress
	 *            address
	 */
	public void setTestAddress(final String testAddress) {
		this.testAddress = testAddress;
	}

	/**
	 * Set notes.
	 * 
	 * @param testNotes
	 *            notes
	 */
	public void setTestNotes(final String testNotes) {
		this.testNotes = testNotes;
	}
}
