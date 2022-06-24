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
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
final class ScalaCompilerFactory {

    // do not instantiate this class
    private ScalaCompilerFactory() { }

    static ScalaCompiler createCompiler(String scalaVersion, Set<File> scalacJars, Logger logger) {
        ScalaInstance scalaInstance = createScalaInstance(scalaVersion, scalacJars);
        RawCompiler compiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto(), new LoggerAdapter(logger));
        return (files, classpath, outputDir) -> {
            try {
                // Make sure that the scala-library is actually available on the classpath.
                findByName(classpath, "library");

                compiler.apply(
                    convertFiles(files),
                    convertFiles(classpath),
                    outputDir.toPath(),
                    JavaConverters.collectionAsScalaIterable(Collections.<String>emptyList()).toSeq()
                );
            } catch (CompileFailed ex) {
                throw new GradleException("Compilation failed.", ex);
            }
        };
    }

    private static scala.collection.Seq<Path> convertFiles(Set<File> files) {
        return JavaConverters.collectionAsScalaIterable(
            files
                .stream()
                .map(File::toPath)
                .collect(
                    Collectors.toSet()
                )
        ).toSeq();
    }

    /**
     * @param scalaVersion The full version of Scala that is configured for this project, e.g. 2.12.9
     * @param scalacJars The full set of JAR files that are necessary for the compiler itself
     */
    private static ScalaInstance createScalaInstance(String scalaVersion, Set<File> scalacJars) {
        ClassLoader classLoader = createClassLoader(scalacJars);
        File libraryJar = findByName(scalacJars, "library");
        File compilerJar = findByName(scalacJars, "compiler");
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
    private static File findByName(Set<File> jarFiles, String name) {
        Objects.requireNonNull(name, "The given name must not be null.");
        return jarFiles.stream()
            .filter(Objects::nonNull)
            .filter(file ->
                file.getName().startsWith("scala-" + name) ||
                file.getName().startsWith("scala3-" + name) ||
                file.getName().startsWith("dotty-" + name)
            )
            .findFirst()
            .orElseThrow(() ->
                new GradleException(
                    "Cannot find the JAR file for 'scala-" + name + "' or 'dotty-" + name + "' in '" + jarFiles + "' " +
                        "Did you forget to declare a dependency? Please make sure that the correct version of the " +
                        "scala library is on the compile classpath, e.g. by adding " +
                        "`implementation 'org.scala-lang:scala-library:2.13.8'`.")
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
