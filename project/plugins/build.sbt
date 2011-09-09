libraryDependencies += "org.clapper" %% "sbt-lwm" % "0.1.4"

libraryDependencies += "org.clapper" %% "sbt-izpack" % "0.1.4"

libraryDependencies += "org.clapper" %% "sbt-editsource" % "0.4.2"

libraryDependencies <<= (sbtVersion, scalaVersion, libraryDependencies) {
    (sbtv, scalav, deps) =>
    deps :+ "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt" + sbtv)
}

//libraryDependencies ++= Seq(
//    "net.databinder" % "sbt-pamflet_2.8.1" % "0.1"
//)


