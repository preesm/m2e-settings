# M2E Settings

## What does this fork add ?

*   In the `<file><location>` tags, the paths supported are the same as 
in the m2e-code-quality plugins, that is classpath, filesystem or 
project relative.
*   The specified maven plugin is a dummy one whose execution does 
nothing, but simply allows to store some additional configuration in the
POM file. This allows a central configuration instead of having to play
with the maven compiler and the maven eclipse plugin at different places.

## Description

Provide consistent Eclipse IDE settings for your team from a Maven POM.
The M2E Settings plugin will copy formatting, findbugs and other plugin
settings from a centrally maintained settings JAR to your workspace and
configure each project to use those settings.

 - uses the Maven Eclipse Plugin settings as a base
 - configure once, set everywhere
 - version control your settings

Many thanks to [Olivier Nouguier](https://github.com/cheleb) for the
[first version of this plugin](https://github.com/cheleb/m2e-settings).

This project is licensed under the [MIT license](https://github.com/topicusonderwijs/m2e-settings/blob/master/LICENSE.txt).

### Table of Contents

 - [Installation](#installation)
 - [Configuration](#configuration)
 - [Building a release](https://github.com/topicusonderwijs/m2e-settings/blob/master/readme.markdown#releasing)

## Installation

Update site URL:

 - https://antoine-morvan.github.io/m2e-settings/site/

### Installing the Eclipse plugin

- Add a new update site to your Eclipse settings:
- Select "Install new software" (OS X: under Help)
- Click "Add"
- Fill in the Name field: "M2E Settings plugin"
- Fill in the Location field: "https://github.com/topicusonderwijs/m2e-settings/raw/master/site"
- Click "OK"
- Click "Next" ad infinitum

## Configuration

This plugin reads the configuration from the POM file. It follows the structure of the [Maven Eclipse Plugin](http://maven.apache.org/components/plugins/maven-eclipse-plugin/eclipse-mojo.html#additionalConfig) additionalConfig for files only (the path can be classpath, systemfile, project relative thanks to the resource resolver from [m2e-code-quality](https://github.com/m2e-code-quality/m2e-code-quality)). However to avoid having M2Eclipse throwing errors, we created a dummy [m2e-settings-maven-plugin](https://github.com/preesm/preesm-maven/tree/master/m2e-settings-maven-plugin) that does nothing when called, but allow to write the configuration in the POM file.

The default configuration would look like:

```xml
<profile>
  <id>only-eclipse</id>
  <activation>
    <property>
      <name>m2e.version</name>
    </property>
  </activation>
  <build>
    <plugins>
      <plugin>
        <groupId>org.preesm.maven</groupId>
        <artifactId>m2e-settings-maven-plugin</artifactId>
        <version>1.0.1</version>
        <executions>
          <execution>
            <id>load-eclipse-settings</id>
            <phase>initialize</phase>
            <goals>
              <goal>m2e-settings</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <additionalConfig>
            <file>
              <name>.settings/org.eclipse.jdt.core.prefs</name>
              <location>/local/path/to/org.eclipse.jdt.core.prefs</location>
            </file>
          </additionalConfig>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

### Using a Settings Jar

One can use a Jar to centralize the settings. This allows to share them among several projects.

There are three steps to configure the M2E Settings:

1. Create (and deploy) your own settings jar (or use one from someone else)
2. Configure the M2E settings plugin in your project
3. Re-import the Maven projects in Eclipse

#### Create your own settings jar

Create a project for your own settings jar. This project will only
contain the relevant Eclipse settings files for your plugins.

##### Create a Maven project

First create an empty Maven project, and put this in the POM to build
your settings jar (adjust the values for your own settings jar).

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.settings</groupId>
    <artifactId>eclipse-settings</artifactId>
    <packaging>jar</packaging>
    <build>
        <defaultGoal>package</defaultGoal>
        <resources>
            <resource>
                <directory>files</directory>
                <filtering>false</filtering>
                <includes>
                    <include>**/*</include>
                </includes>
            </resource>
        </resources>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.0.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

This configures Maven to look in the `files` folder for resources and
package them into the resulting jar.

##### Add your settings to the JAR

Now you can copy the various Eclipse settings from the `.settings`
folders into the files folder:

```bash
$ ls settings-project/files
-rw-r--r--   1 dashorst  staff     55 Jul  7 17:52 edu.umd.cs.findbugs.plugin.eclipse.prefs
-rw-r--r--   1 dashorst  staff    529 Jul  7 17:52 org.eclipse.core.resources.prefs
-rw-r--r--   1 dashorst  staff    175 Jul  7 17:52 org.eclipse.jdt.apt.core.prefs
-rw-r--r--   1 dashorst  staff  31543 Jul  7 17:52 org.eclipse.jdt.core.prefs
-rw-r--r--   1 dashorst  staff  11723 Jul  7 17:52 org.eclipse.jdt.ui.prefs
-rw-r--r--   1 dashorst  staff     86 Jun 29 23:47 org.eclipse.m2e.core.prefs
-rw-r--r--   1 dashorst  staff    411 Jun 29 23:52 org.eclipse.wst.common.component
-rw-r--r--   1 dashorst  staff    167 Jun 29 23:52 org.eclipse.wst.common.project.facet.core.xml
-rw-r--r--   1 dashorst  staff    382 Jul  7 17:52 org.eclipse.wst.validation.prefs
-rw-r--r--   1 dashorst  staff    232 Jul  7 17:52 org.maven.ide.eclipse.prefs
```

You can repeat this every time a new version of Eclipse comes out, and
update all settings to new defaults.

##### Deploy to a Maven repository

Now you can upload the jar to a Maven repository using `mvn deploy`. Or
use the Maven release plugin to create releases of your settings jar.

Note that you can also install the jar in your local repository using
`mvn install`. This will however make the settings available for you only.

#### Configure M2E settings in your project

The M2E Settings plugin retrieves the Eclipse workspace settings from
the [Maven Eclipse Plugin][1] configuration. The easiest way to provide
these settings is to create a resource JAR file and distribute that
from a Maven repository. You then specify your 'settings JAR' file as a
dependency to the *maven-eclipse-plugin*.

As the M2E Settings plugin needs to be bound to a Maven lifecycle, we
bind its execution to the initialize phase (any phase from the default
lifecyle would do).

The *maven-eclipse-plugin* allows you to [move settings files from one
location to another](2). This is done in the `<additionalConfig>` tags.

Finally, to prevent the execution of that plugin during a 'normal' maven
build, we put all that configuration in a profile that is activated only
by M2Eclipse.

A sample configuration with such jar dependency would look like the 
following blob (execution and configuration tags can be taken from the 
example above).

``` xml
<plugin>
  <groupId>org.preesm.maven</groupId>
  <artifactId>m2e-settings-maven-plugin</artifactId>
  <version>1.0.1</version>
    <dependency>
      <groupId>org.preesm.maven</groupId>
      <artifactId>coding-policy</artifactId>
      <version>${preesm-settings-version}</version>
    </dependency>
  </dependencies>
  <!-- execution + configuration -->
</plugin>
```

#### Re-import projects in Eclipse

Now we have modified the projects, you have to re-import the projects
in Eclipse. Typically this is done by:

 - selecting all projects,
 - right-clicking on the selection and
 - clicking "Maven → Update project"

## Releasing

If you are a developer of this project and have made some modifications use
this guide to build a release to distribute it to the users.

### Building a release

Run the release shell script from the root folder of the m2e-settings
project:

``` bash
./release.sh
```

This script performs the following steps:

- assign a new release version number to the current workspace
- create a new distribution of the new version in the current workspace
- create an updated P2 repository in the current workspace
- commit all these results into the git repository

This doesn't push the intermediate results to github, this is a manual
step you have to do to release a new version.

## Uploading a release

When you have checked the release and it is found OK, then you can
upload the new version to github and instruct your team to perform an
update:

```
git push
```

This will push the changes to github and publish a new update site to
the update site URL.


[1]: http://maven.apache.org/plugins/maven-eclipse-plugin
[2]: http://maven.apache.org/plugins/maven-eclipse-plugin/eclipse-mojo.html#additionalConfig
