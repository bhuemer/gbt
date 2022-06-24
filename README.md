# Gradle Build Tool

[![CircleCI](https://circleci.com/gh/bhuemer/gbt.svg?style=shield)](https://circleci.com/gh/bhuemer/gbt)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A plugin that enables the use of SBTs most recent compilers in Gradle Scala projects.

## Installation

GBT is published as a Gradle plugin via [plugins.gradle.org](https://plugins.gradle.org/):

```groovy
plugins {
    id 'com.github.bhuemer.gbt' version '0.2'
}

// By default it will use Scala 2.13.8, but you can configure the version.
scalac {
    scalaVersion '2.13.8'
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
    implementation 'org.scala-lang:scala-library:2.13.8'
}
```

## Configuration

Similar to the built-in Scala plugin this one mostly depends on [Zinc](https://github.com/sbt/zinc) for the actual
compilation. The plugin will automatically resolve the correct compiler bridges for you based on the `scalaVersion`
you have provided, but in more complicated cases you can also specify those manually. For example, this is how you 
would configure a `build.gradle` file for Scala 3:

```groovy
plugins {
    id 'com.github.bhuemer.gbt' version '0.2'
}

scalac {
    scalaVersion = '3.0.2'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation "org.scala-lang:scala3-library_3:3.0.2"
}
```

## Next steps

- [ ] Actually implement / make use of incremental compilation. At the moment this plugin just uses the `RawCompiler` 
    as I wanted to sort out classpath and project set-up issues first before tackling incremental compilation.
- [ ] Scaladoc generation task
- [ ] Better configuration for the compiler (e.g. allowing users to pass all the various language feature flags)