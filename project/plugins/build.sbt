libraryDependencies += "org.clapper" %% "sbt-lwm" % "0.1.2"

libraryDependencies += "org.clapper" %% "sbt-izpack" % "0.1.2"

libraryDependencies <<= (sbtVersion, scalaVersion, libraryDependencies) {
    (sbtv, scalav, deps) =>
    deps :+ "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt" + sbtv)
}
