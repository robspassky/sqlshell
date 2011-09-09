import sqlshell.build.defs._

// ---------------------------------------------------------------------------
// Basic settings

name := "SQLShell"

version := "0.8"

organization := "org.clapper"

scalaVersion := "2.9.1"

// ---------------------------------------------------------------------------
// Additional compiler options and plugins

scalacOptions ++= Seq("-P:continuations:enable", "-deprecation", "-unchecked")

autoCompilerPlugins := true

libraryDependencies <<= (scalaVersion, libraryDependencies) { (ver, deps) =>
    deps :+ compilerPlugin("org.scala-lang.plugins" % "continuations" % ver)
}

// ---------------------------------------------------------------------------
// Local settings. See project/defs.scala

seq(sqlshell.build.defs.sqlshellSettings: _*)

// Ensure that the about info properties file is built before packaging.

packageBin in Compile <<=
  (packageBin in Compile).dependsOn(aboutInfo in SQLShell)


// ---------------------------------------------------------------------------
// SBT LWM

seq(org.clapper.sbt.lwm.LWM.lwmSettings: _*)

LWM.sourceFiles in LWM.Config <++= baseDirectory { d =>
    (d / "src" / "docs" ** "*.md").get
}

LWM.sourceFiles in LWM.Config <++= baseDirectory { d => (d / "README.md").get ++
                                                  (d / "LICENSE.md").get ++
                                                  (d / "FAQ.md").get }

LWM.targetDirectory in LWM.Config <<= baseDirectory(_ / "target" / "docs")

LWM.cssFile in LWM.Config <<=
  baseDirectory(d => Some(d / "src" / "docs" / "markdown.css"))

LWM.flatten in LWM.Config := true

LWM.encoding in LWM.Config := "ISO-8859-1"

// ---------------------------------------------------------------------------
// IzPack

seq(org.clapper.sbt.izpack.IzPack.izPackSettings: _*)

IzPack.installSourceDir in IzPack.Config <<=
  baseDirectory(_ / "src" / "main" / "izpack")

IzPack.configFile in IzPack.Config <<=
  (IzPack.installSourceDir in IzPack.Config) (_ / "install.yml")

IzPack.variables in IzPack.Config += ("toolName", "SQLShell")

IzPack.variables in IzPack.Config <++= baseDirectory {bd =>
    Seq(("targetDocDir", (bd / "target" / "docs").toString),
        ("targetDir", (bd / "target").toString))
}

IzPack.variables in IzPack.Config <+=
  (baseDirectory, scalaVersion, version) { (bd, sv, v) =>
  ("sqlshellJar", (bd / "target" / ("scala_" + sv) / 
                   ("sqlshell_%s-%s.jar" format (sv, v))).toString)
}

IzPack.createXML in IzPack.Config <<=
  (IzPack.createXML in IzPack.Config).dependsOn(LWM.translate in LWM.Config)

// ---------------------------------------------------------------------------
// Pamflet

//seq(net.databinder.pamflet_plugin.PamfletPlugin.pamfletSettings: _*)

//pamfletSourceDirs in Pamflet <<= baseDirectory(bd => 
//    Seq(bd / "src" / "docs" / "users-guide")
//)

//pamfletLogLevel in Pamflet := Level.Debug

// Force an edit of the pamflet template properties file, to substitute
// variables.

//generate in Pamflet <<= (generate in Pamflet).dependsOn(edit in EditSource)

// ---------------------------------------------------------------------------
// Edit Source settings. Only used to preprocess Pamflet stuff.

seq(org.clapper.sbt.editsource.EditSource.editSourceSettings: _*)

EditSource.sourceFiles in EditSource.Config <+= baseDirectory(
    _ / "src" / "docs" / "pamflet-template.properties"
)

EditSource.targetDirectory in EditSource.Config <<= baseDirectory(
  _ / "src" / "docs" / "users-guide"
)

EditSource.flatten in EditSource.Config := true

EditSource.variables in EditSource.Config <+= name {name => ("name", name)}

EditSource.variables in EditSource.Config <+= 
  version {version => ("version", version)}

// ---------------------------------------------------------------------------
// Other dependendencies

libraryDependencies ++= Seq(
    "jline" % "jline" % "0.9.94",
    "org.clapper" %% "grizzled-scala" % "1.0.8",
    "org.clapper" %% "argot" % "0.3.5",
    "org.joda" % "joda-convert" % "1.1",
    "joda-time" % "joda-time" % "2.0",
    "org.scala-tools.time" %% "time" % "0.5",
    "net.sf.opencsv" % "opencsv" % "2.0"
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-library" % _)

// ---------------------------------------------------------------------------
// Publishing criteria

publishTo <<= version {(v: String) =>
    val nexus = "http://nexus.scala-tools.org/content/repositories/"
    if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "snapshots/") 
    else                             Some("releases"  at nexus + "releases/")
}

publishMavenStyle := true

credentials += Credentials(Path.userHome / "src" / "mystuff" / "scala" /
                           "nexus.scala-tools.org.properties")


// ---------------------------------------------------------------------------
// Tasks

