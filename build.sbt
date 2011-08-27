// ---------------------------------------------------------------------------
// Basic settings

name := "sqlshell"

version := "0.8"

organization := "org.clapper"

scalaVersion := "2.9.0-1"

// ---------------------------------------------------------------------------
// Additional compiler options and plugins

scalacOptions ++= Seq("-P:continuations:enable", "-deprecation", "-unchecked")

autoCompilerPlugins := true

libraryDependencies <<= (scalaVersion, libraryDependencies) { (ver, deps) =>
    deps :+ compilerPlugin("org.scala-lang.plugins" % "continuations" % ver)
}

// ---------------------------------------------------------------------------
// ScalaTest dependendency

libraryDependencies <<= (scalaVersion, libraryDependencies) { (sv, deps) =>
    // Select ScalaTest version based on Scala version
    val scalatestVersionMap = Map("2.8.0"   -> ("scalatest", "1.3"),
                                  "2.8.1"   -> ("scalatest_2.8.1", "1.5.1"),
                                  "2.9.0"   -> ("scalatest_2.9.0", "1.6.1"),
                                  "2.9.0-1" -> ("scalatest_2.9.0-1", "1.6.1"))
    val (scalatestArtifact, scalatestVersion) = scalatestVersionMap.getOrElse(
        sv, error("Unsupported Scala version: " + scalaVersion)
    )
    deps :+ "org.scalatest" % scalatestArtifact % scalatestVersion % "test"
}

crossScalaVersions := Seq("2.9.0-1", "2.8.1")

// ---------------------------------------------------------------------------
// IzPack

seq(org.clapper.sbt.izpack.IzPack.izPackSettings: _*)

installSourceDir in IzPack <<= baseDirectory(_ / "src" / "main" / "izpack")

configFile in IzPack <<= (installSourceDir in IzPack) (_ / "install.yml")


// ---------------------------------------------------------------------------
// SBT LWM

seq(org.clapper.sbt.lwm.LWM.lwmSettings: _*)

sourceFiles in LWM <++= baseDirectory { d =>
    (d / "src" / "docs" ** "*.md").get
}

targetDirectory in LWM <<= baseDirectory(_ / "target" / "docs")

cssFile in LWM <<= baseDirectory(d => Some(d / "src" / "docs" / "markdown.css"))

flatten in LWM := true

// ---------------------------------------------------------------------------
// Posterous-SBT

libraryDependencies <<= (sbtVersion, scalaVersion, libraryDependencies) { (sbtv, scalav, deps) =>
    if (scalav == "2.8.1")
        deps :+ "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt" + sbtv)
    else
        deps
}

(name in Posterous) := "Grizzled Scala"

// ---------------------------------------------------------------------------
// Other dependendencies

libraryDependencies ++= Seq(
    "jline" % "jline" % "0.9.94",
    "org.clapper" %% "grizzled-scala" % "1.0.7",
    "org.clapper" %% "argot" % "0.3.3",
    "org.joda" % "joda-convert" % "1.1",
    "joda-time" % "joda-time" % "2.0",
    "org.scala-tools.time" %% "time" % "0.4",
    "net.sf.opencsv" % "opencsv" % "2.0"
)

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


