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

/**
 *
 */
@SuppressWarnings("WeakerAccess")
public class ScalaPluginExtension {

    /** The name of the DSL block to use to configure the Scala plugin. */
    static final String EXTENSION_NAME = "scalac";

    /** By default we'll assume Scala 2.12 is used for Scala projects. */
    private static final String DEFAULT_VERSION_SCALA = "2.12.8";

    private String scalaVersion;

    /**
     * Returns the Scala version that is configured for this project, or a default Scala version.
     */
    public String getScalaVersion() {
        return scalaVersion != null ? scalaVersion : DEFAULT_VERSION_SCALA;
    }

    /**
     * Allows you to override the Scala version that you want to use.
     */
    @SuppressWarnings("unused")
    public void setScalaVersion(String scalaVersion) {
        this.scalaVersion = scalaVersion;
    }

    /**
     * Determines the major Scala version for whatever is configured for this Scala project.
     */
    public String getScalaMajorVersion() {
        String[] parts = getScalaVersion().split("\\.");
        if (parts.length == 2 || parts.length == 3) {
            return parts[0] + "." + parts[1];
        } else {
            throw new IllegalStateException(
                "Scala version '" + getScalaVersion() + "' is not supported. Cannot determine the major version.");
        }
    }

}
