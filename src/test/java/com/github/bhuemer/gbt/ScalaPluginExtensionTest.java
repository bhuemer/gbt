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
