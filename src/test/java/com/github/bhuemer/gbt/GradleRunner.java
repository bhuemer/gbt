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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.InvalidRunnerConfigurationException;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.gradle.testkit.runner.UnexpectedBuildSuccess;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;

/**
 *
 */
class GradleRunner implements Closeable  {

    private final org.gradle.testkit.runner.GradleRunner runner;

    private GradleRunner(org.gradle.testkit.runner.GradleRunner runner) {
        this.runner = runner;
    }

    /**
     * Creates a new Gradle runner for the given project. The name corresponds to a folder in our test
     * resources directory, i.e. it has to exist statically, but can be arbitrarily complex in return.
     * @param name The name of the folder in the test resources folder that you want to use as project root
     * @return a Gradle runner pre-configured for the given project
     */
    static GradleRunner forProject(String name) throws IOException {
        URL url = GradleRunner.class.getClassLoader().getResource("./" + name);
        if (url == null || url.getFile() == null || url.getFile().isEmpty()) {
            throw new IllegalStateException(
                "Cannot find the test project '" + name + "' in the classpath.");
        }

        // Create a temporary folder for
        //  (a) the project as defined - otherwise the fact that it's nested within another Gradle project
        //      (this one!) would cause issues and potential confusion. This way it's more disconnected.
        //  (b) any output we might want to produce (e.g. as part of compiling the project)
        File projectDir = Files.createTempDirectory("scala-gradle-test").toFile();
        copy(new File(url.getFile()), projectDir);

        return new GradleRunner(org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withDebug(true));
    }

    /**
     * Returns the temporary directory that the build will be executed in.
     * @return The temporary directory that the build will be executed in
     */
    public File getProjectDir() {
        return runner.getProjectDir();
    }

    public GradleRunner withBuildFile(String... lines) throws IOException {
        writeLines(new File(runner.getProjectDir(), "build.gradle"), lines);
        return this;
    }

    public GradleRunner withArguments(String... strings) {
        return new GradleRunner(runner.withArguments(strings));
    }

    /**
     * Executes a build, expecting it to complete without failure.
     */
    public BuildResult build() throws InvalidRunnerConfigurationException, UnexpectedBuildFailure {
        return runner.build();
    }

    /**
     * Executes a build, expecting it to complete with failure.
     */
    public BuildResult buildAndFail() throws InvalidRunnerConfigurationException, UnexpectedBuildSuccess {
        return runner.buildAndFail();
    }

    /** Closes this Gradle runner by deleting all the temporary files we have copied and/or created. */
    @Override
    public void close() throws IOException {
        delete(runner.getProjectDir());
    }

    /**
     */
    private static void copy(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.mkdirs();
            }

            String[] files = source.list();
            if (files == null) return;
            for (String file : files) {
                copy(new File(source, file), new File(target, file));
            }
        } else {
            Files.copy(source.toPath(), target.toPath());
        }
    }

    /**
     * Utility method that recursively deletes all files and directories in the given file/directory.
     */
    private static void delete(File file) throws IOException {
        if (file == null) {
            return;
        }

        File[] files = file.listFiles();
        if (files != null) {
            for (File each : files) {
                delete(each);
            }
        }

        Files.delete(file.toPath());
    }

    private static void writeLines(File file, String[] lines) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(String.join("\n", lines));
        }
    }

}
