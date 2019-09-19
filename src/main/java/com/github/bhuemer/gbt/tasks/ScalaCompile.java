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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.*;
import org.gradle.work.InputChanges;

import java.io.File;

/**
 * Compiles Scala source files.
 */
public class ScalaCompile extends DefaultTask {

    /** The logger instance for this task. */
    private static final Logger logger = Logging.getLogger(ScalaCompile.class);

    private String scalaVersion;

    /** The JAR files containing the compiler and all its possible dependencies */
    private FileCollection scalacClasspath;

    /** The source files that we want to compile */
    private FileCollection source;

    private File destinationDir;

    /** The compile classpath */
    private FileCollection classpath;

    @Input
    public String getScalaVersion() {
        return scalaVersion;
    }

    public void setScalaVersion(String scalaVersion) {
        this.scalaVersion = scalaVersion;
    }

    @Classpath
    public FileCollection getScalacClasspath() {
        return scalacClasspath;
    }

    public void setScalacClasspath(FileCollection scalacClasspath) {
        this.scalacClasspath = scalacClasspath;
    }

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(org.gradle.api.tasks.PathSensitivity.ABSOLUTE)
    public FileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @SuppressWarnings("UnstableApiUsage")
    @TaskAction
    public void compile(InputChanges changes) {
        logger.info("Compiling using Scala " + getScalaVersion());

        ScalaCompiler compiler = ScalaCompilerFactory.createCompiler(
            getScalaVersion(),
            getScalacClasspath().getFiles(),
            logger
        );
        compiler.compile(
            getSource().getFiles(),
            getClasspath().getFiles(),
            getDestinationDir()
        );
    }

}
