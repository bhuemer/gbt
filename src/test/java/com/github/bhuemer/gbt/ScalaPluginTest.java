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

import org.gradle.api.GradleException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ScalaPluginTest {

    /**
     * Makes sure that the Scala compile tasks are registered correctly and appear in the list of all tasks.
     */
    @Test
    public void compileScalaAppearsAsTask() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleProject")) {
            BuildResult result = runner.withArguments("tasks", "--all").build();
            assertThat(result.getOutput(), containsString("compileScala - Compiles main Scala source."));
            assertThat(result.getOutput(), containsString("compileTestScala - Compiles test Scala source."));
        }
    }

    /**
     * Makes sure that the `compileScala` task produces the expected .class files.
     */
    @Test
    public void compileScalaProducesClassFiles() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleProject")) {
            BuildResult result = runner.withArguments("compileScala").build();

            assertThat(result.getTasks().get(0), was(":compileJava", TaskOutcome.NO_SOURCE));
            assertThat(result.getTasks().get(1), was(":compileScala", TaskOutcome.SUCCESS));

            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/App.class").exists());
            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/App$.class").exists());
        }
    }

    /**
     */
    @Test
    public void compileScalaClassFilesAreIncludedInJarFiles() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleProject")) {
            BuildResult result = runner.withArguments("assemble").build();

            // Make sure that compileScala was executed despite not being invoked explicitly
            assertThat(result.getTasks(), hasItem(was(":compileScala", TaskOutcome.SUCCESS)));
            assertThat(result.getTasks(), hasItem(was(":jar", TaskOutcome.SUCCESS)));
            assertThat(result.getTasks(), hasItem(was(":assemble", TaskOutcome.SUCCESS)));

            File buildLibs = new File(runner.getProjectDir(), "build/libs");
            File[] jarFiles = buildLibs.listFiles(file -> file.getName().endsWith(".jar"));
            assertThat(jarFiles, arrayWithSize(1));

            Set<String> jarFileEntryNames = collectZipFileEntryNames(jarFiles[0]);
            assertThat(jarFileEntryNames, containsInAnyOrder(
                "App$.class",
                "App.class",
                "META-INF/",
                "META-INF/MANIFEST.MF"
            ));
        }
    }

    /**
     * Makes sure that the `compileTestScala` task depends correctly on all other relevant tasks and has access to
     * output generated from them (i.e. the classpath is set up correctly).
     */
    @Test
    public void compileTestScalaDependsOnCompileScala() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleProject")) {
            BuildResult result = runner.withArguments("compileTestScala").build();

            assertThat(result.getTasks(), hasItem(was(":compileJava", TaskOutcome.NO_SOURCE)));
            assertThat(result.getTasks(), hasItem(was(":compileScala", TaskOutcome.SUCCESS)));
            assertThat(result.getTasks(), hasItem(was(":compileTestJava", TaskOutcome.NO_SOURCE)));
            assertThat(result.getTasks(), hasItem(was(":compileTestScala", TaskOutcome.SUCCESS)));

            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/test/AppSpec.class").exists());
        }
    }

    /**
     * Makes sure that Scala 2.13 projects can be compiled successfully as well.
     */
    @Test
    public void compileScalaWorksWithScala213() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleProject213")) {
            BuildResult result = runner.withArguments("compileScala").build();

            assertThat(result.getTasks().get(0), was(":compileJava", TaskOutcome.NO_SOURCE));
            assertThat(result.getTasks().get(1), was(":compileScala", TaskOutcome.SUCCESS));

            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/App.class").exists());
            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/App$.class").exists());
        }
    }

    /**
     * Makes sure that errors are displayed correctly when you try to compile a 2.13 project with a 2.12 compiler.
     */
    @Test
    public void compileWithWrongScalaVersion() throws Exception {
        try (GradleRunner runner = GradleRunner
                .forProject("testSimpleProject213")
                .withBuildFile(
                    "plugins {                      ",
                    "   id 'com.github.bhuemer.gbt' ",
                    "}                              ",
                    "                               ",
                    "scalac {                       ",
                    "   scalaVersion = '2.12.8'     ",
                    "}                              ",
                    "                               ",
                    "repositories {                 ",
                    "   jcenter()                   ",
                    "}                              ",
                    "                               ",
                    "dependencies {                 ",
                    "   implementation 'org.scala-lang:scala-library:2.12.8'",
                    "}"
                )) {
            BuildResult result = runner.withArguments("compileScala").buildAndFail();

            assertThat(result.getTasks().get(0), was(":compileJava", TaskOutcome.NO_SOURCE));
            assertThat(result.getTasks().get(1), was(":compileScala", TaskOutcome.FAILED));

            assertThat(result.getOutput(), containsString("object jdk is not a member of package scala"));
        }
    }

    /**
     * Makes sure that IntelliJ IDEA files are generated correctly for Scala projects.
     */
    @Test
    public void generateSimpleIdeaProject() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleProjectWithIdea")) {
            BuildResult result = runner.withArguments("cleanIdea", "idea").build();

            assertThat(result.getTasks(), hasItem(was(":ideaProject",   TaskOutcome.SUCCESS)));
            assertThat(result.getTasks(), hasItem(was(":ideaModule",    TaskOutcome.SUCCESS)));
            assertThat(result.getTasks(), hasItem(was(":ideaWorkspace", TaskOutcome.SUCCESS)));
            assertThat(result.getTasks(), hasItem(was(":idea",          TaskOutcome.SUCCESS)));

            // Make sure that all the relevant IDEA files have been generated
            File[] ideaFiles = runner.getProjectDir().listFiles(file ->
                file.getName().endsWith(".iml") ||
                file.getName().endsWith(".ipr") ||
                file.getName().endsWith(".iws")
            );
            assertThat(ideaFiles, arrayWithSize(3));

            File imlFile = Stream.of(ideaFiles)
                .filter(file -> file.getName().endsWith(".iml"))
                .findFirst()
                // This shouldn't ever be possible at this stage but nonetheless
                .orElseThrow(() -> new GradleException("Couldn't find the .iml file in output directory."));

            // Make sure that the Scala SDK has been declared in the .iml file
            String imlFileContent = String.join("\n", Files.readAllLines(imlFile.toPath()));
            assertThat(imlFileContent, containsString(
                "<orderEntry type=\"library\" name=\"scala-sdk-2.12.8\" level=\"application\"/>"));
        }
    }

    /**
     * Makes sure that Dotty projects can be compiled with this plugin as well.
     */
    @Test
    public void compileDottyProjects() throws Exception {
        try (GradleRunner runner = GradleRunner.forProject("testSimpleDotty")) {
            BuildResult result = runner.withArguments("compileScala").build();

            assertThat(result.getTasks().get(0), was(":compileJava", TaskOutcome.NO_SOURCE));
            assertThat(result.getTasks().get(1), was(":compileScala", TaskOutcome.SUCCESS));

            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/EnumTypes.class").exists());
            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/EnumTypes.tasty").exists());
            assertTrue(new File(runner.getProjectDir(), "build/classes/scala/main/EnumTypes$ListEnum$.class").exists());
        }
    }

    // ------------------------------------------ Utility methods

    /**
     * Collects the names of all the ZIP entries that appear in the given file.
     */
    private static Set<String> collectZipFileEntryNames(File file) throws IOException {
        Set<String> result = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                result.add(entry.getName());
            }
        }
        return result;
    }

    /**
     * Creates a new Hamcrest matcher for the given build task.
     */
    private static Matcher<BuildTask> was(final String path, final TaskOutcome outcome) {
        return new BaseMatcher<BuildTask>() {
            @Override
            public void describeTo(Description description) {
                description.appendText(path).appendText("=").appendText(outcome.name());
            }

            @Override
            public boolean matches(Object item) {
                if (!(item instanceof BuildTask)) {
                    return false;
                }

                BuildTask buildTask = (BuildTask) item;
                return Objects.equals(path, buildTask.getPath())
                    && Objects.equals(outcome, buildTask.getOutcome());
            }
        };
    }

}
