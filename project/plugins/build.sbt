libraryDependencies ++= Seq("org.clapper" %% "sbt-lwm" % "0.1.5",
                            "org.clapper" %% "sbt-izpack" % "0.1.4",
                            "org.clapper" %% "sbt-editsource" % "0.4.2",
                            "org.clapper" %% "sbt-pamflet" % "0.1")

libraryDependencies <<= (sbtVersion, scalaVersion, libraryDependencies) {
    (sbtv, scalav, deps) =>
    deps :+ "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt" + sbtv)
}
