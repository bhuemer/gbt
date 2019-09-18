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
package com.github.bhuemer.gbt.tasks;

import com.github.bhuemer.gbt.tasks.support.LoggerAdapter;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import sbt.internal.inc.CompileFailed;
import sbt.internal.inc.RawCompiler;
import sbt.internal.inc.ScalaInstance;
import scala.Option;
import scala.collection.JavaConverters;
import xsbti.compile.ClasspathOptionsUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
final class ScalaCompilerFactory {

    // do not instantiate this class
    private ScalaCompilerFactory() { }

    static ScalaCompiler createCompiler(String scalaVersion, Set<File> scalacJars, Logger logger) {
        ScalaInstance scalaInstance = createScalaInstance(scalaVersion, scalacJars);
        RawCompiler compiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.boot(), new LoggerAdapter(logger));
        return (files, classpath, outputDir) -> {
            try {
                compiler.apply(
                    JavaConverters.collectionAsScalaIterable(files).toSeq(),
                    JavaConverters.collectionAsScalaIterable(classpath).toSeq(),
                    outputDir,
                    JavaConverters.collectionAsScalaIterable(Collections.<String>emptyList()).toSeq()
                );
            } catch (CompileFailed ex) {
                throw new GradleException("Compilation failed.", ex);
            }
        };
    }

    /**
     * @param scalaVersion The full version of Scala that is configured for this project, e.g. 2.12.9
     * @param scalacJars The full set of JAR files that are necessary for the compiler itself
     */
    private static ScalaInstance createScalaInstance(String scalaVersion, Set<File> scalacJars) {
        ClassLoader classLoader = createClassLoader(scalacJars);
        File libraryJar = findByName(scalacJars, "scala-library");
        File compilerJar = findByName(scalacJars, "scala-compiler");
        return new ScalaInstance(
            scalaVersion,
            classLoader,
            classLoader,
            new File[] {
                libraryJar
            },
            compilerJar,
            scalacJars.toArray(new File[0]),
            Option.empty()
        );
    }

    /**
     * Returns the file that matches the given name or throws an exception otherwise.
     */
    private static File findByName(Set<File> scalacJars, String name) {
        Objects.requireNonNull(name, "The given name must not be null.");
        return scalacJars.stream()
            .filter(file -> file != null && file.getName().startsWith(name))
            .findFirst()
            .orElseThrow(() ->
                new GradleException("Cannot find the JAR file for '" + name + "' in '" + scalacJars + "'.")
            );
    }

    /**
     * Creates a new URL class loader for the given JAR files.
     */
    private static ClassLoader createClassLoader(Set<File> jarFiles) {
        Objects.requireNonNull(jarFiles, "The given set of JAR files must not be null.");
        return new URLClassLoader(jarFiles.stream()
            .map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (MalformedURLException ex) {
                    // This shouldn't ever happen really.
                    throw new GradleException(
                        "Cannot build classloader for Scala compiler: "
                            + file + " cannot be converted to a URL.", ex);
                }
            })
            .toArray(URL[]::new), null);
    }

}
