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
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.BuildAdapter;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultScalaSourceSet;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.language.scala.plugins.ScalaLanguagePlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Set;

import static org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject.findOrCreateFirstChildWithAttributeValue;

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

    private void configureOptionalPlugins(Project project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {
                IdeaModel model = project.getExtensions().findByType(IdeaModel.class);
                if (model == null) {
                    return;
                }

                model.getModule().getIml().withXml(provider -> {
                    Node iml = provider.asNode();

                    Node newModuleRootManager = findOrCreateFirstChildWithAttributeValue(iml, "component", "name", "NewModuleRootManager");

                    Node sdkLibrary = findOrCreateFirstChildWithAttributeValue(newModuleRootManager, "orderEntry", "name", "scala-sdk-2.18");
                    sdkLibrary.attributes().put("type", "library");
                    sdkLibrary.attributes().put("level", "project");
                });
            }
        });
    }

    private void configureExtensions(Project project) {
        ScalaPluginExtension configuration =
            project.getExtensions().create(ScalaPluginExtension.EXTENSION_NAME, ScalaPluginExtension.class);
        project.getTasks()
            .withType(ScalaCompile.class)
            .configureEach(scalaCompile -> {
                scalaCompile.setScalaVersion(configuration.getScalaVersion());
                scalaCompile.setScalacJars(resolveScalacClasspath(configuration, project));
            });
    }

    private void configureSourceSets(Project project) {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(sourceSet -> {
            ScalaSourceSet scalaSourceSet = sourceSet.getExtensions().create(
                "scala", DefaultScalaSourceSet.class, String.format("%s Scala source", sourceSet.getName()), project.getObjects());
            Convention convention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
            convention.getPlugins().put("scala", scalaSourceSet);

            final SourceDirectorySet scalaDirectorySet = scalaSourceSet.getScala();
            scalaDirectorySet.srcDir(project.file(String.format("src/%s/scala", sourceSet.getName())));
            scalaDirectorySet.setOutputDir(project.provider(() ->
                // Set the output directory to something like "classes/scala/main"
                project.getBuildDir().toPath()
                    .resolve("classes")
                    .resolve(scalaDirectorySet.getName())
                    .resolve(sourceSet.getName())
                    .toFile()
            ));

            // Register a corresponding Scala compile task for this source set,
            // e.g. compileScale for src/main, compileTestScala for src/test, etc.
            project.getTasks().register(
                sourceSet.getCompileTaskName("scala"),
                ScalaCompile.class,
                scalaCompile -> {
                    scalaCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                    scalaCompile.setDescription(String.format("Compiles %s Scala source.", sourceSet.getName()));
                    scalaCompile.setSource(scalaDirectorySet);
                    scalaCompile.setClasspath(
                        sourceSet.getCompileClasspath().plus(project.files(sourceSet.getJava().getOutputDir()))
                    );
                    scalaCompile.setDestinationDir(project.provider(scalaDirectorySet::getOutputDir));
                }
            );
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

}
