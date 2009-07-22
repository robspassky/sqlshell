import sbt._
import scala.io.Source
import java.io.{File, FileWriter, PrintWriter}

/**
 * To build SQLShell via SBT.
 */
class SQLShellProject(info: ProjectInfo) extends DefaultProject(info)
{
    /* ---------------------------------------------------------------------- *\
                         Compiler and SBT Options
    \* ---------------------------------------------------------------------- */

    override def compileOptions = Unchecked :: super.compileOptions.toList

    override def parallelExecution = true // why not?

    /* ---------------------------------------------------------------------- *\
                             Various settings
    \* ---------------------------------------------------------------------- */

    val izPackHome = 
        if (System.getenv("IZPACK_HOME") != null)
            System.getenv("IZPACK_HOME")
        else
            pathFor(System.getProperty("user.home"), "java", "IzPack")

    /* ---------------------------------------------------------------------- *\
                               Custom tasks
    \* ---------------------------------------------------------------------- */

    // Build the installer jar. Delegates to buildInstaller() 
    lazy val installer = task {buildInstaller; None}
                         .dependsOn(packageAction) 
                         .describedAs("Build installer.")

    /* ---------------------------------------------------------------------- *\
                       Managed External Dependencies
    \* ---------------------------------------------------------------------- */

    val scalaToolsRepo = "Scala-Tools Maven Repository" at 
        "http://scala-tools.org/repo-releases/"

    val scalatest = "org.scala-tools.testing" % "scalatest" % "0.9.5"
    val joptSimple = "net.sf.jopt-simple" % "jopt-simple" % "3.1"
    val jodaTime = "joda-time" % "joda-time" % "1.6"
    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    // Grizzled comes from local machine for now. This works, though, as long
    // as someone has done a publish-local.
    val grizzled = "grizzled-scala" % "grizzled-scala" % "0.1"

    /* ---------------------------------------------------------------------- *\
                          Private Helper Methods
    \* ---------------------------------------------------------------------- */

    private def pathFor(pieces: List[String]): String = 
        pieces mkString File.separator

    private def pathFor(pieces: String*): String = pathFor(pieces.toList)

    /**
     * Edits a file, substituting variable references. Variable references
     * look like @var@. The supplied map is used to find variable values; the
     * keys are the variable names, without the @ characters. Any variable that
     * isn't found in the map is silently ignored.
     *
     * @param in    the source file to read
     * @param vars  the variables
     *
     * @return the edited lines in the file
     */
    private def editFile(in: Source, vars: Map[String, String]): List[String] =
    {
        def doEdits(line: String, keys: List[String]): String =
        {
            keys match
            {
                case Nil => 
                    line

                case key :: tail =>
                    val value = vars(key)
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
                    doEdits(line, vars.keys.toList) :: editLines(tail)
            }
        }

        editLines(in.getLines.toList)
    }

    /**
     * Simplified front-end to FileUtilities.clean()
     */
    private def cleanDir(dir: Path) = FileUtilities.clean(dir, log)

    /**
     * Build the actual installer jar.
     */
    private def buildInstaller =
    {
        if (! new File(izPackHome).exists)
            throw new Exception("Can't run IzPack compiler. No valid " +
                                "IZPACK_HOME.")

        // Determine what's to be installed.

        val installDir = mainSourcePath / "izpack"
        val installFile = installDir / "install.xml"

        // Copy the third party jars (minus the ones we don't want) to a
        // temporary directory.

        FileUtilities.withTemporaryDirectory(log)
        {
            jarDir =>

            // Get the list of jar files to include, besides the project's
            // jar. Note to self: "**" means "recursive drill down". "*"
            // means "immediate descendent".

            val jars = ("lib" +++ "lib_managed") **
                       ("*.jar" - "izpack*.jar" - "scalatest*.jar")
            val jarDirPath = Path.fromFile(jarDir)
            log.info("Copying jars to \"" + jarDir + "\"")
            FileUtilities.copyFlat(jars.get, jarDirPath, log)

            // Create the IzPack configuration file from the template.

            log.info("Creating IzPack configuration file.")
            val temp = File.createTempFile("inst", ".xml")
            temp.deleteOnExit

            val vars = Map(
                "API_DOCS_DIR" -> ("target"/"doc"/"main"/"api").absolutePath,
                "DOCS_DIR" -> Path.fromFile("docs").absolutePath,
                "JAR_FILE" -> jarPath.absolutePath,
                "LICENSE" -> path("LICENSE.html").absolutePath,
                "README" -> path("README.html").absolutePath,
                "SQLSHELL_VERSION" -> projectVersion.value.toString,
                "SRC_INSTALL" -> installDir.absolutePath,
                "THIRD_PARTY_JAR_DIR" -> jarDir.getPath,
                "TOP_DIR" -> path(".").absolutePath
            )

            val out = new PrintWriter(new FileWriter(temp))
            try
            {
                for (line <- editFile(Source.fromFile(installFile.absolutePath),
                                      vars))
                    out.print(line)
            }
            finally
            {
                out.close
            }

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
        }
    }
}
