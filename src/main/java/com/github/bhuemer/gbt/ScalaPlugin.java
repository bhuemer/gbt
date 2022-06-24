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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.Cast;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * A plugin which compiles Scala source files.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class ScalaPlugin implements Plugin<Project> {

    /**
     * The name to use to configure the scalac dependencies, e.g.
     * ```
     * dependencies {
     *     scalac 'ch.epfl.lamp:dotty-compiler_0.18:0.18.1-RC1'
     *     scalac 'ch.epfl.lamp:dotty-sbt-bridge:0.18.1-RC1'
     * }
     * ```
     */
    private static final String CONFIGURATION_NAME = ScalaPluginExtension.EXTENSION_NAME;

    /** The logger instance for this class. */
    private static final Logger logger = Logging.getLogger(ScalaPlugin.class);

    /**
     * Entry point that applies this plugin to the given project.
     */
    @Override
    public void apply(@Nonnull Project project) {
        configureRequiredPlugins(project);
        configureConfigurations(project);
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
    }

    private void configureConfigurations(Project project) {
        project.getConfigurations().create(CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("Dependencies required for the Scala compiler")
            .defaultDependencies(dependencies -> {
                ScalaPluginExtension runtime = project.getExtensions().getByType(ScalaPluginExtension.class);

                Dependency scalaCompiler;
                Dependency bridgeCompiler;

                if (runtime.isScala3()) {
                    scalaCompiler = project.getDependencies().create("org.scala-lang:scala3-compiler_3:" + runtime.getScalaVersion());
                    bridgeCompiler = project.getDependencies().create("org.scala-lang:scala3-sbt-bridge:" + runtime.getScalaVersion());
                } else {
                    scalaCompiler = project.getDependencies().create("org.scala-lang:scala-compiler:" + runtime.getScalaVersion());
                    bridgeCompiler = project.getDependencies().create("org.scala-sbt:compiler-bridge_" + runtime.getScalaMajorVersion() + ":1.6.1");
                }

                if (logger.isDebugEnabled()) {
                    String dependencyInfo = String.join(
                            "\n", scalaCompiler.toString(), bridgeCompiler.toString());
                    logger.debug("Adding default dependencies for compilation: \n" + dependencyInfo);
                }
                dependencies.add(scalaCompiler);
                dependencies.add(bridgeCompiler);
            });
    }

    private void configureExtensions(Project project) {
        ScalaPluginExtension configuration =
            project.getExtensions().create(ScalaPluginExtension.EXTENSION_NAME, ScalaPluginExtension.class);
        project.getTasks()
            .withType(ScalaCompile.class)
            .configureEach(scalaCompile -> {
                scalaCompile.setScalaVersion(configuration.getScalaVersion());
                scalaCompile.setScalacClasspath(
                    project.getConfigurations().getByName(CONFIGURATION_NAME)
                );
            });
    }

    /**
     * Creates additional source directory sets (e.g. `src/main/scala` and `src/test/scala`) and configures
     * the relevant compile task for each of these source sets (e.g. `compileScala` and `compileTestScala`).
     */
    private void configureSourceSets(final Project project) {
        SourceSetContainer sourceSets = getSourceSets(project);

        // Both should really exist, but we don't strictly insist on it here
        SourceSet mainSourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet testSourceSet = sourceSets.findByName(SourceSet.TEST_SOURCE_SET_NAME);

        // Register the Scala source directory set and the compile task for all source sets, even if it's
        // neither the main nor the test source set. This allows you to configure source sets for integration
        // tests, etc. and they'll get picked up as well. However, the task dependencies need to be set manually
        // in those cases (e.g. `classpath` and `dependsOn`) .. Some meaningful defaults will already be set.
        sourceSets.all(sourceSet -> {
            final SourceDirectorySet scalaDirectorySet = project.getObjects().sourceDirectorySet(
                sourceSet.getName(), String.format("%s Scala source", sourceSet.getName()));
            scalaDirectorySet.srcDir(project.file(String.format("src/%s/scala", sourceSet.getName())));
            scalaDirectorySet.getDestinationDirectory().convention(
                project.getLayout().getBuildDirectory().dir(String.format("classes/scala/%s", sourceSet.getName()))
            );
            sourceSet.getExtensions().add("scala", scalaDirectorySet);

            // Make sure that the class files generated by the Scala compiler are also picked up
            // as part of the the output for this source set (e.g. when assembling JAR files, etc.).
            DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
            sourceSetOutput.addClassesDir(scalaDirectorySet.getDestinationDirectory());

            // TODO: Ideally we would adjust the various sourceSet classpaths as well to make it easier for
            // other people to build additional tasks without having to know too much about how to build these
            // classpaths themselves?

            // Register the corresponding Scala compile task for this source set
            project.getTasks().register(
                sourceSet.getCompileTaskName("scala"),
                ScalaCompile.class,
                scalaCompile -> {
                    if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName()) && mainSourceSet != null) {
                        scalaCompile.dependsOn(
                            sourceSet.getCompileJavaTaskName(),             // :compileTestJava
                            mainSourceSet.getCompileTaskName("scala")       // :compileScala
                        );

                        // For test compile tasks make sure that outputs from all the
                        // other relevant tasks are available during compilation.
                        scalaCompile.setClasspath(sourceSet.getCompileClasspath().plus(project.files(
                            sourceSet.getJava().getClassesDirectory(),      // classes for src/test/java
                            mainSourceSet.getJava().getClassesDirectory(),  // classes for src/main/java
                            determineOutputDirFor(project, mainSourceSet)   // classes for src/main/scala
                        )));
                    } else {
                        // By default just make it depend on the equivalent Java compile task.
                        scalaCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                        scalaCompile.setClasspath(
                            sourceSet.getCompileClasspath().plus(
                                project.files(sourceSet.getJava().getClassesDirectory())
                            )
                        );
                    }

                    scalaCompile.setDescription(String.format("Compiles %s Scala source.", sourceSet.getName()));
                    scalaCompile.setDestinationDir(determineOutputDirFor(project, sourceSet));
                    scalaCompile.setSource(scalaDirectorySet);
                }
            );

            // Make sure that `compileScala` gets called whenever a task depends on `classes`, etc.
            project.getTasks()
                .getByName(sourceSet.getClassesTaskName())
                .dependsOn(sourceSet.getCompileTaskName("scala"));
        });
    }

    /**
     * Makes sure that if an IDE plugin is available in this project, it will be configured correctly.
     *
     * At the moment this only takes care of IntelliJ IDEA:
     *  1) Makes sure that the source folders are added to the modules correctly.
     *  2) Makes sure that a Scala SDK is declared. Ideally we'd also define what the Scala SDK is, but
     *     we'll leave that for later.
     */
    private void configureIdeModules(final Project project) {
        project.afterEvaluate(ignored -> {
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

                boolean isTest = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName());

                SourceDirectorySet sds = (SourceDirectorySet) sourceSet.getExtensions().findByName("scala");
                if (sds == null) {
                    logger.debug("Not configuring IDE module for source set '" + sourceSet.getName()
                        + "': No Scala directory set available.");
                    return;
                }

                IdeaConfigurer.includeSourceDirs(project, sds.getSrcDirs(), isTest);
            });

            ScalaPluginExtension extension = project.getExtensions().getByType(ScalaPluginExtension.class);
            String scalaSdkName = extension.getScalaSdkName();
            if (scalaSdkName == null || scalaSdkName.isEmpty()) {
                scalaSdkName = String.format("scala-sdk-%s", extension.getScalaVersion());
            }

            IdeaConfigurer.includeScalaSdkDependency(project, scalaSdkName);
        });
    }

    /**
     * Determines the output directory for the Scala compile task that handles the given source set,
     * e.g. `build/classes/scala/main` for the main source set, etc.
     *
     * @param project The project for which we are configuring the Scala plugin
     * @param sourceSet The source set for which we want to determine the output directory
     */
    private static File determineOutputDirFor(Project project, SourceSet sourceSet) {
        return project.getBuildDir().toPath()
            .resolve("classes")
            .resolve("scala")
            .resolve(sourceSet.getName())
            .toFile();
    }

    private static SourceSetContainer getSourceSets(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
    }

}
