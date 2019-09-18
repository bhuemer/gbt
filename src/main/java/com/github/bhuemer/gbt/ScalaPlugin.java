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
import groovy.util.Node;
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
import org.gradle.plugins.ide.idea.model.IdeaModule;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public class ScalaPlugin implements Plugin<Project> {

    /** The logger instance for this class. */
    private static final Logger logger = Logging.getLogger(ScalaPlugin.class);

    /**
     * Entry point that applies this plugin to the given project.
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
            scalaDirectorySet.setOutputDir(determineOutputDirFor(project, sourceSet));
            sourceSet.getExtensions().add("scala", scalaDirectorySet);

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
                            sourceSet.getJava().getOutputDir(),             // classes for src/test/java
                            mainSourceSet.getJava().getOutputDir(),         // classes for src/main/java
                            determineOutputDirFor(project, mainSourceSet)   // classes for src/main/scala
                        )));
                    } else {
                        // By default just make it depend on the equivalent Java compile task.
                        scalaCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                        scalaCompile.setClasspath(
                            sourceSet.getCompileClasspath().plus(project.files(sourceSet.getJava().getOutputDir()))
                        );
                    }

                    scalaCompile.setDescription(String.format("Compiles %s Scala source.", sourceSet.getName()));
                    scalaCompile.setDestinationDir(scalaDirectorySet.getOutputDir());
                    scalaCompile.setSource(scalaDirectorySet);
                }
            );
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
            // Only do this once all the IDE plugins have been registered already.
            IdeaModule module = Optional.ofNullable(project.getExtensions().findByType(IdeaModel.class))
                .map(IdeaModel::getModule)
                .orElse(null);
            if (module == null) {
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
                if (isTest) {
                    module.setTestSourceDirs(union(module.getTestSourceDirs(), scalaDirectorySet.getSrcDirs()));
                } else {
                    module.setSourceDirs(union(module.getSourceDirs(), scalaDirectorySet.getSrcDirs()));
                }
            });

            ScalaPluginExtension extension = project.getExtensions().findByType(ScalaPluginExtension.class);
            if (extension == null || extension.getScalaVersion() == null) {
                logger.info("Not configuring the Scala SDK.");
                return;
            }

            // Manually modify .iml files to include an <orderEntry /> node that refers to the Scala SDK
            // Note that this unfortunately assumes that the SDK has been configured already. However,
            // seeing that those are defined globally (i.e. independent of individual projects), it seems
            // okay-ish to ask the users to define those once through a few clicks. Will fix this properly
            // later.
            module.getIml().withXml(xmlProvider -> {
                Node node = xmlProvider.asNode();
                for (Object obj : node.children()) {
                    if (obj instanceof Node) {
                        Node child = (Node) obj;
                        if ("NewModuleRootManager".equals(child.attribute("name"))) {
                            Map<String, String> attributes = new LinkedHashMap<>();
                            attributes.put("type", "library");
                            attributes.put("name", String.format("scala-sdk-%s", extension.getScalaVersion()));
                            attributes.put("level", "application");
                            child.appendNode("orderEntry", attributes);
                        }
                    }
                }
            });
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

    /**
     * Determines the output directory for the Scala compile task that handles the given source set, e.g.
     * `build/classes/scala/main` for the main source set, etc.
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
        return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> result = new HashSet<>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }

}
