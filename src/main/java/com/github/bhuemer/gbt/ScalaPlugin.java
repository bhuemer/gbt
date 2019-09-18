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

import com.github.bhuemer.gbt.tasks.ScalaCompile;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.language.scala.plugins.ScalaLanguagePlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class ScalaPlugin implements Plugin<Project> {

    /** The logger instance for this class. */
    private static final Logger logger = Logging.getLogger(ScalaPlugin.class);

    /**
     *
     */
    @Override
    public void apply(@Nonnull Project project) {
        configureRequiredPlugins(project);
        configureExtensions(project);
        configureSourceSets(project);
        configureIdeModules(project);
    }

    /**
     * Makes sure that all the required plugins are applied to the given project.
     */
    private void configureRequiredPlugins(Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        // Enable this to make sure that Gradle's IDE plugins recognize this as a Scala module/project
        project.getPluginManager().apply(ScalaLanguagePlugin.class);
    }

    private void configureExtensions(Project project) {
        ScalaPluginExtension configuration =
            project.getExtensions().create(ScalaPluginExtension.EXTENSION_NAME, ScalaPluginExtension.class);
        project.getTasks()
            .withType(ScalaCompile.class)
            .configureEach(scalaCompile -> {
                scalaCompile.setScalaVersion(configuration.getScalaVersion());
                // TODO: Defer this classpath resolution to when the compiler actually runs.
                scalaCompile.setScalacJars(resolveScalacClasspath(configuration, project));
            });
    }

    /**
     * Creates additional source directory sets (e.g. `src/main/scala` and `src/test/scala`) and configures
     * the relevant compile task for each of these source sets (e.g. `compileScala` and `compileTestScala`).
     */
    private void configureSourceSets(final Project project) {
        final SourceSetContainer sourceSets = getSourceSets(project);
        sourceSets.all(sourceSet -> {
            final SourceDirectorySet scalaDirectorySet = project.getObjects().sourceDirectorySet(
                sourceSet.getName(), String.format("%s Scala source", sourceSet.getName()));
            scalaDirectorySet.srcDir(project.file(String.format("src/%s/scala", sourceSet.getName())));
            scalaDirectorySet.setOutputDir(
                // Set the output directory to something like "classes/scala/main"
                project.getBuildDir().toPath()
                    .resolve("classes")
                    .resolve(scalaDirectorySet.getName())
                    .resolve(sourceSet.getName())
                    .toFile()
            );
            sourceSet.getExtensions().add("scala", scalaDirectorySet);

            // Register the corresponding Scala compile task for this source set
            project.getTasks().register(
                sourceSet.getCompileTaskName("scala"),
                ScalaCompile.class,
                scalaCompile -> {
                    // TODO: Make this also depend on compileScala if we are configuring compileTestScala, etc.
                    scalaCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                    scalaCompile.setDescription(String.format("Compiles %s Scala source.", sourceSet.getName()));
                    scalaCompile.setSource(scalaDirectorySet);
                    scalaCompile.setClasspath(
                        sourceSet.getCompileClasspath().plus(project.files(sourceSet.getJava().getOutputDir()))
                    );
                    scalaCompile.setDestinationDir(scalaDirectorySet.getOutputDir());
                }
            );
        });
    }

    @SuppressWarnings({"ConstantConditions", "unchecked"})
    private void configureIdeModules(final Project project) {
        project.afterEvaluate(ignored -> {
            // Only do this once all the IDE plugins have been registered already.
            final IdeaModel model = project.getExtensions().findByType(IdeaModel.class);
            if (model == null) {
                return;
            }

            getSourceSets(project).all(sourceSet -> {
                // If somebody adds custom source sets for integration tests, etc. we cannot automatically
                // figure out whether we should add those scala sources as `srcDir` or `testSrcDir`, so we
                // will limit ourselves here to the source sets that we can definitely figure out.
                if (!SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) &&
                    !SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    logger.debug("Not configuring IDE module for source set '" + sourceSet.getName()
                        + "': Unknown source set.");
                    return;
                }

                SourceDirectorySet scalaDirectorySet =
                    (SourceDirectorySet) sourceSet.getExtensions().findByName("scala");
                if (scalaDirectorySet == null) {
                    logger.debug("Not configuring IDE module for source set '" + sourceSet.getName()
                        + "': No Scala directory set available.");
                    return;
                }

                boolean isTest = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName());

                Set<File> srcDirs = new HashSet<>();
                if (isTest) {
                    srcDirs.addAll(model.getModule().getTestSourceDirs());
                } else {
                    srcDirs.addAll(model.getModule().getSourceDirs());
                }
                srcDirs.addAll(scalaDirectorySet.getSrcDirs());
                if (isTest) {
                    model.getModule().setTestSourceDirs(srcDirs);
                } else {
                    model.getModule().setSourceDirs(srcDirs);
                }
            });

            final ScalaPluginExtension extension = project.getExtensions().findByType(ScalaPluginExtension.class);
            if (extension == null || extension.getScalaVersion() == null) {
                logger.info("Not configuring the Scala SDK.");
            }
        });
    }

    /**
     * Resolves and downloads all the necessary files for the Scala compiler in the given version.
     */
    private Set<File> resolveScalacClasspath(ScalaPluginExtension runtime, Project project) {
        try {
            Dependency scalaCompiler = project.getDependencies().create("org.scala-lang:scala-compiler:" + runtime.getScalaVersion());
            Dependency bridgeCompiler = project.getDependencies().create("org.scala-sbt:compiler-bridge_" + runtime.getScalaMajorVersion() + ":1.3.0");
            return project.getConfigurations()
                .detachedConfiguration(scalaCompiler, bridgeCompiler)
                .resolve();
        } catch (GradleException ex) {
            throw new GradleException("Could not determine the Scalac classpath. Make sure that you are (a) " +
                "using the correct Scala version and (b) have at least one repository defined. To configure the " +
                "Scala version add a DSL block like \n```\nscalac {\n  scalaVersion = '2.13.0'\n}\n``` to your " +
                "build.gradle file. The version that is configured currently is " +
                    "'" + runtime.getScalaVersion() + "'.", ex);
        }
    }

    private static SourceSetContainer getSourceSets(Project project) {
        return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
    }

}
