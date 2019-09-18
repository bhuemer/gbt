package com.github.bhuemer.gbt;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ScalaPluginTest {

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Test
    public void compileScalaAppearsAsTask() throws Exception {
        File buildFile = testProjectDir.newFile("build.gradle");
        writeFile(buildFile, "plugins { id 'com.github.bhuemer.gbt' } \nscalac { scalaVersion = '2.12.8' }\nrepositories { mavenCentral() }\ndependencies { implementation 'org.scala-lang:scala-library:2.12.8' }");

        testProjectDir.newFolder("src", "main", "scala");

        File mainApp = testProjectDir.newFile("src/main/scala/App.scala");
        writeFile(mainApp, "object App { println(\"Hello World!\") }");

        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("compileScala", "--stacktrace")
            .withPluginClasspath()
            .withDebug(true)
            .build();

        System.out.println(result.getOutput());
    }

    /** Writes the given content to the temporary file. */
    private static void writeFile(File destination, String content) throws IOException {
        try (BufferedWriter output = new BufferedWriter(new FileWriter(destination))) {
            output.write(content);
        }
    }

}
