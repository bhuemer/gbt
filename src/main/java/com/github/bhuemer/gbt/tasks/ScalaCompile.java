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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.AbstractCompile;

import java.io.File;
import java.util.Set;

public class ScalaCompile extends AbstractCompile {

    /** The logger instance for this task. */
    private static final Logger logger = Logging.getLogger(ScalaCompile.class);

    private String scalaVersion;

    /** The JAR files that are necessary to compile */
    private Set<File> scalacJars;

    public void setScalaVersion(String scalaVersion) {
        this.scalaVersion = scalaVersion;
    }

    public void setScalacJars(Set<File> scalacJars) {
        this.scalacJars = scalacJars;
    }

    @Override
    @TaskAction
    protected void compile() {
        logger.info("Compiling using Scala " + scalaVersion);

        ScalaCompiler compiler = ScalaCompilerFactory.createCompiler(scalaVersion, scalacJars, logger);
        compiler.compile(
            getSource().getFiles(),
            getClasspath().getFiles(),
            getDestinationDir()
        );
    }

}
