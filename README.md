# Gradle Build Tool

[![CircleCI](https://circleci.com/gh/bhuemer/gbt.svg?style=shield)](https://circleci.com/gh/bhuemer/gbt)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A plugin that enables the use of SBTs most recent compilers in Gradle Scala projects.

## Installation

GBT will hopefully soon be published as a Gradle plugin via [plugins.gradle.org](https://plugins.gradle.org/). Once 
that has been approved, the plugin will be available to you in your `build.gradle` file:

```groovy
plugins {
    id 'com.github.bhuemer.gbt'
}

// By default it will use Scala 2.13.8, but you can configure the version.
scalac {
    scalaVersion '2.13.0'
}

// The plugin needs to be able to resolve SBT and Scala library/compiler 
// JARs as dependencies. You can use whatever repository you prefer though.
repositories {
    jcenter()
}

dependencies {
    // The plugin neither infers the Scala version from this dependency nor 
    // will it add it automatically for you. If you don't have it declared
    // compilation will fail.
    implementation 'org.scala-lang:scala-library:2.13.0'
}
```

In the meantime you can use this plugin by publishing it to your own local maven repository (`./gradlew publishToMavenLocal`), 
for example, and then also including the following bit of configuration:

```groovy
buildscript {
    repositories {
        mavenLocal()
    }

    dependencies {
        classpath "com.github.bhuemer.gbt:gbt:0.1-SNAPSHOT"
    }
}
```

## Configuration

Similar to the built-in Scala plugin this one mostly depends on [Zinc](https://github.com/sbt/zinc) for the actual
compilation. The plugin will automatically resolve the correct compiler bridges for you based on the `scalaVersion`
you have provided, but in more complicated cases you can also specify those manually. For example, this is how you 
would configure a `build.gradle` file for Dotty / Scala 3:

```groovy
plugins {
    id 'com.github.bhuemer.gbt'
}

scalac {
    scalaVersion = '0.18.1-RC1'
}

repositories {
    mavenCentral()
}

dependencies {
    scalac 'ch.epfl.lamp:dotty-compiler_0.18:0.18.1-RC1'
    scalac 'ch.epfl.lamp:dotty-sbt-bridge:0.18.1-RC1'
    // Again, the library needs to be added manually:
    implementation 'ch.epfl.lamp:dotty-library_0.18:0.18.1-RC1'
}
```

## Next steps

- [ ] Actually implement / make use of incremental compilation. At the moment this plugin just uses the `RawCompiler` 
    as I wanted to sort out classpath and project set-up issues first before tackling incremental compilation.
- [ ] Scaladoc generation task
- [ ] Better configuration for the compiler (e.g. allowing users to pass all the various language feature flags)