import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
    // Managed dependencies that are used by the project file itself.
    // Putting them here allows them to be imported in the project class.

    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    val grizzled = "org.clapper" % "grizzled-scala" % "0.2" from
    "http://www.clapper.org/software/scala/grizzled-scala/grizzled-scala-0.2.jar"
    // My Maven repo.

    val clapperMavenRepo = "clapper.org Maven Repo" at
        "http://maven.clapper.org/"

    val markdown = "org.clapper" % "sbt-markdown-plugin" % "0.1.1"
    val editsource = "org.clapper" % "sbt-editsource-plugin" % "0.1.1"
    val izpackPlugin = "org.clapper" % "sbt-izpack-plugin" % "0.1.1"
}
