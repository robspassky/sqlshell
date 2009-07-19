import sbt._

class SQLShellProject(info: ProjectInfo) extends DefaultProject(info)
{
    override def compileOptions = Unchecked :: super.compileOptions.toList

    // External dependencies

    val scalaToolsRepo = "Scala-Tools Maven Repository" at 
        "http://scala-tools.org/repo-releases/org/scala-tools/testing/scalatest/0.9.5/"

    val scalatest = "org.scala-tools.testing" % "scalatest" % "0.9.5"
    val joptSimple = "net.sf.jopt-simple" % "jopt-simple" % "3.1"
    val jodaTime = "joda-time" % "joda-time" % "1.6"
    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    // Grizzled comes from local machine for now
    val grizzled = "grizzled-scala-library" % "grizzled-scala-library" % "0.1" from 
        "http://darkroom.inside.clapper.org/~bmc/code/grizzled-scala-library-0.1.jar"
}
