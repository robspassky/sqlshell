import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
    // Managed dependencies that are used by the project file itself.
    // Putting them here allows them to be imported in the project class.

    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    val grizzled = "org.clapper" % "grizzled-scala" % "0.2" from
    "http://www.clapper.org/software/scala/grizzled-scala/grizzled-scala-0.2.jar"
    val markdown = "org.clapper" % "sbt-markdown-plugin" % "0.1" from
    "http://github.com/downloads/bmc/sbt-plugins/sbt-markdown-plugin-0.1.jar"
}
