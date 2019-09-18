# Gradle Build Tool

[![CircleCI](https://circleci.com/gh/bhuemer/gbt.svg?style=shield)](https://circleci.com/gh/bhuemer/gbt)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A plugin that enables the use of SBTs most recent incremental compilers in Gradle Scala projects.

## Installation

GBT will hopefully soon be published as a Gradle plugin via [plugins.gradle.org](https://plugins.gradle.org/). Once 
that has been approved, the plugin will be available to you in your `build.gradle` file:

```groovy
plugins {
    id 'com.github.bhuemer.gbt' version '0.1'
}

// By default it will use Scala 2.12.8, but you can configure the version.
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