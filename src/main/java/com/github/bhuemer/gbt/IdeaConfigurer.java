package com.github.bhuemer.gbt;

import groovy.util.Node;
import org.gradle.api.Project;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods related to configuring IntelliJ IDEA projects and modules.
 */
@SuppressWarnings("WeakerAccess")
public final class IdeaConfigurer {

    // Do not instantiate this class
    private IdeaConfigurer() { }

    /**
     * Changes IDEA module generation for this project such that the given source directories will be included as such.
     * @param project The Gradle project for which we want to adjust IDEA module generation
     * @param srcDirs The source directories that you want to have included in IDEA modules
     * @param isTest Whether or not to include these as production code (`false`) or test code (`true`)
     */
    public static void includeSourceDirs(Project project, Set<File> srcDirs, boolean isTest) {
        IdeaModel model = project.getExtensions().findByType(IdeaModel.class);
        if (model == null) {
            return;
        }

        if (isTest) {
            model.getModule().setTestSourceDirs(union(model.getModule().getTestSourceDirs(), srcDirs));
        } else {
            model.getModule().setSourceDirs(union(model.getModule().getSourceDirs(), srcDirs));
        }
    }

    /**
     * Changes IDEA module generation for this project such that references to the given Scala SDK will be included.
     *
     * These SDK references are assumed to be defined at application-level, i.e. once per IntelliJ IDEA installation,
     * which seems like a good enough compromise at the moment. Users don't have to adjust project settings whenever
     * IDEA modules are re-generated, only the very first time requires them to actually configure the SDK, and we
     * don't have to implement that at the moment.
     *
     * @param project The Gradle project for which we want to adjust IDEA module generation
     * @param scalaSdkName The name of the Scala SDK to use
     */
    public static void includeScalaSdkDependency(Project project, String scalaSdkName) {
        IdeaModel model = project.getExtensions().findByType(IdeaModel.class);
        if (model == null) {
            return;
        }

        // Manually modify .iml files to include an <orderEntry /> node that refers to the Scala SDK.
        model.getModule().getIml().withXml(xmlProvider -> {
            Node node = xmlProvider.asNode();
            for (Object obj : node.children()) {
                if (obj instanceof Node) {
                    Node child = (Node) obj;
                    if ("NewModuleRootManager".equals(child.attribute("name"))) {
                        Map<String, String> attributes = new LinkedHashMap<>();
                        attributes.put("type", "library");
                        attributes.put("name", scalaSdkName);
                        attributes.put("level", "application");
                        child.appendNode("orderEntry", attributes);
                    }
                }
            }
        });
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> result = new HashSet<>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }

}
