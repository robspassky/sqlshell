import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info)
{
    // Managed dependencies that are used by the project file itself.
    // Putting them here allows them to be imported in the project class.

    val markdownj = "org.markdownj" % "markdownj" % "0.3.0-1.0.2b4"
    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    // Grizzled comes from local machine for now. This works, though, as long
    // as someone has done a publish-local.
    val grizzled = "org.clapper" % "grizzled-scala" % "0.1"

    val ocutil = "org.clapper" % "ocutil" % "2.4.2" from
    "http://www.clapper.org/software/java/util/download/2.4.2/ocutil-2.4.2.jar"
}
