/*
 * Copyright (c) 2019 Bernhard Huemer (bernhard.huemer@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.bhuemer.gbt;

import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ScalaPluginExtensionTest {

    /**
     * Makes sure that the major Scala version can be determined from whatever is configured for the project.
     */
    @Test
    public void majorScalaVersion() {
        Function<String, String> test = given -> {
            ScalaPluginExtension extension = new ScalaPluginExtension();
            extension.setScalaVersion(given);
            return extension.getScalaMajorVersion();
        };

        assertEquals("2.11", test.apply("2.11.8"));
        assertEquals("2.11", test.apply("2.11.9"));
        assertEquals("2.12", test.apply("2.12.7"));
        assertEquals("2.12", test.apply("2.12.8"));
        assertEquals("2.12", test.apply("2.12.9"));
        assertEquals("2.13", test.apply("2.13.0"));
        assertEquals("2.13", test.apply("2.13.1"));

        try {
            test.apply("2");
            fail("Should not have been possible to determine the major Scala version for '2'.");
        } catch (IllegalStateException expected) { }
    }

}
