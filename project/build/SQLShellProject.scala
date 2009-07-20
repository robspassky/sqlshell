import sbt._
import scala.io.Source
import java.io.{File, FileWriter, PrintWriter}

/**
 * To build SQLShell via SBT.
 */
class SQLShellProject(info: ProjectInfo) extends DefaultProject(info)
{
    // Compiler options

    override def compileOptions = Unchecked :: super.compileOptions.toList

    // Location of IzPack.

    def izPackHome = 
        if (System.getenv("IZPACK_HOME") != null)
            System.getenv("IZPACK_HOME")
        else
            pathFor(System.getProperty("user.home"), "java", "IzPack")

    // Installation task. Delegates to doInstall() method.

    lazy val installer = task {log.info("stub."); doInstall; None}
        .dependsOn(packageAction) 
        .describedAs("Build installer.")

    // External dependencies

    val scalaToolsRepo = "Scala-Tools Maven Repository" at 
        "http://scala-tools.org/repo-releases/org/scala-tools/testing/scalatest/0.9.5/"

    val scalatest = "org.scala-tools.testing" % "scalatest" % "0.9.5"
    val joptSimple = "net.sf.jopt-simple" % "jopt-simple" % "3.1"
    val jodaTime = "joda-time" % "joda-time" % "1.6"
    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    // Grizzled comes from local machine for now
    val grizzled = "grizzled-scala" % "grizzled-scala" % "0.1" from 
        "http://darkroom.inside.clapper.org/~bmc/code/grizzled-scala-0.1.jar"

    /* ---------------------------------------------------------------------- *\
                          Private Helper Methods
    \* ---------------------------------------------------------------------- */

    private def pathFor(pieces: List[String]): String = 
        pieces mkString File.separator

    private def pathFor(pieces: String*): String = pathFor(pieces.toList)

    private def edit(in: Source, edits: Map[String, String]): List[String] =
    {
        def doEdits(line: String, keys: List[String]): String =
        {
            keys match
            {
                case Nil => 
                    line

                case key :: tail =>
                    val value = edits(key)
                    doEdits(line.replaceAll("@" + key + "@", value), tail)
            }
        }

        def editLines(lines: List[String]): List[String] =
        {
            lines match
            {
                case Nil => 
                    Nil

                case line :: tail =>
                    doEdits(line, edits.keys.toList) :: editLines(tail)
            }
        }

        editLines(in.getLines.toList)
    }

    private def cleanDir(dir: Path) = FileUtilities.clean(dir, log)

    private def doInstall =
    {
        if (! new File(izPackHome).exists)
            throw new Exception("Can't run IzPack intaller. No valid " +
                                "IZPACK_HOME.")

        // Determine what's to be installed.

        val installDirPieces = List(mainSourcePath.absolutePath, "izpack")
        val installDir = pathFor(installDirPieces)
        val installFile = pathFor(installDirPieces ++ List("install.xml"))

        // Copy the third party jars (minus the ones we don't want) to a
        // temporary directory.

        val thirdPartyJarDir = ("target"/"jars")
        cleanDir(thirdPartyJarDir)
        log.info("Creating " + thirdPartyJarDir.relativePath)
        new File(thirdPartyJarDir.relativePath).mkdir

        val jars1 = "lib_managed" ** 
                    ("*.jar" - "izpack*.jar" - "scalatest*.jar")
        val jars2 = "lib" ** "*.jar"
        val jarsToInclude = jars1.get.toList ++ jars2.get.toList
        log.info("Copying jars to \"" + thirdPartyJarDir.relativePath + "\"")
        FileUtilities.copyFlat(jarsToInclude, thirdPartyJarDir, log)

        // Create the IzPack configuration file from the template.

        log.info("Creating IzPack configuration file.")
        val temp = File.createTempFile("inst", ".xml")
        temp.deleteOnExit
        val out = new PrintWriter(new FileWriter(temp))

        val edits = Map("SQLSHELL_VERSION" -> projectVersion.value.toString,
                        "LICENSE" -> path("LICENSE.html").absolutePath,
                        "README" -> path("README.html").absolutePath,
                        "TOP_DIR" -> path(".").absolutePath,
                        "JAR_FILE" -> jarPath.absolutePath,
                        "THIRD_PARTY_JAR_DIR" -> thirdPartyJarDir.absolutePath,
                        "SRC_INSTALL" -> installDir)

        for (line <- edit(Source.fromFile(installFile), edits))
            out.print(line)
        out.close
        println(temp.getPath)

        // Run IzPack. Have to load the class, since it's not going to be
        // available when this file is compiled.

        log.info("Creating installer jar.")
        import sbt.Run.{run => runClass}
        val izPath = "lib_managed" / "compile" * "*.jar"
        val args = List(temp.getPath,
                        "-h", izPackHome,
                        "-b", ".",
                        "-o", ("target"/"install.jar").absolutePath,
                        "-k", "standard")
        runClass("com.izforge.izpack.compiler.Compiler", 
                 izPath.get,
                 args,
                 log)

        cleanDir(thirdPartyJarDir)
    }
}
