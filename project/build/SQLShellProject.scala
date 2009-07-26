import sbt._

import scala.io.Source

import java.io.{File, FileWriter, PrintWriter}

import grizzled.file.implicits._

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

    val sourceDocsDir = "src" / "docs"
    val targetDocsDir = "target" / "doc"
    val usersGuide = sourceDocsDir / "users-guide.md"
    val markdownFiles = (path(".") * "*.md") +++ usersGuide
    val markdownHtmlFiles = transformPaths(targetDocsDir,
                                           markdownFiles,
                                           {_.replaceAll("\\.md$", ".html")})
    val markdownSources = markdownFiles +++ (sourceDocsDir / "markdown.css")

    /* ---------------------------------------------------------------------- *\
                               Custom tasks
    \* ---------------------------------------------------------------------- */

    // Build the installer jar. Delegates to installerAction() 
    lazy val installer = task {installerAction; None}
                         .dependsOn(packageAction, docAction)
                         .describedAs("Build installer.")

    // Create the target/docs directory
    lazy val makeTargetDocsDir = task 
    {
        FileUtilities.createDirectory(targetDocsDir, log)
    }

    // Generate HTML docs from Markdown sources
    lazy val htmlDocs = fileTask(markdownHtmlFiles from markdownSources)
    { 
        markdown("README.md", targetDocsDir / "README.html")
        markdown("BUILDING.md", targetDocsDir / "BUILDING.html")
        markdown("LICENSE.md", targetDocsDir / "LICENSE.html")
        markdown(usersGuide, targetDocsDir / "users-guide.html")
        None
    } 
    .dependsOn(makeTargetDocsDir)

    // Copy Markdown sources into target/docs
    lazy val markdownDocs = copyTask(markdownFiles, targetDocsDir)

    // Local doc production
    lazy val targetDocs = task {None} dependsOn(htmlDocs, markdownDocs)

    // Override the "doc" action to depend on additional doc targets
    override def docAction = super.docAction dependsOn(targetDocs)

    // Console with project defs
    lazy val projectConsole = task {Run.projectConsole(this)}

    /* ---------------------------------------------------------------------- *\
                       Managed External Dependencies

               NOTE: Additional dependencies are declared in
         project/plugins/Plugins.scala. (Declaring them there allows them
                       to be imported in this file.)
    \* ---------------------------------------------------------------------- */

    val scalaToolsRepo = "Scala-Tools Maven Repository" at 
        "http://scala-tools.org/repo-releases/"

    val scalatest = "org.scala-tools.testing" % "scalatest" % "0.9.5"
    val joptSimple = "net.sf.jopt-simple" % "jopt-simple" % "3.1"
    val jodaTime = "joda-time" % "joda-time" % "1.6"
    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"

    // Grizzled comes from local machine for now. This works, though, as long
    // as someone has done a publish-local.
    val grizzled = "org.clapper" % "grizzled-scala" % "0.1"

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
     * Run Markdown to convert a source (Markdown) file to HTML.
     *
     * @param source  the path to the source file
     * @param target  the path to the output file
     * @param title   the title for the HTML document
     */
    private def markdown(source: Path, target: Path): Unit =
    {
        import java.io.{FileOutputStream, OutputStreamWriter, PrintWriter}
        import scala.xml.parsing.XhtmlParser

        val classpath = "lib_managed" / "compile" * "*.jar"
        val Encoding = "ISO-8859-1"

        import com.petebevin.markdown.MarkdownProcessor

        val md = new MarkdownProcessor
        log.info("Generating \"" + target + "\" from \"" + source + "\"")
        val cssLines = fileLines(sourceDocsDir / "markdown.css")
        val sourceLines = fileLines(source).toList
        // Title is first line.
        val title = sourceLines.head
        val sHTML = "<body>" + md.markdown(sourceLines mkString "") + "</body>"
        val body = XhtmlParser(Source.fromString(sHTML))
        val out = new PrintWriter(
                      new OutputStreamWriter(
                          new FileOutputStream(target.absolutePath), Encoding))
        val contentType = "text/html; charset=" + Encoding
        val html = 
<html>
<head>
<title>{title}</title>
<style type="text/css">
{cssLines mkString ""}
</style>
<meta http-equiv="content-type" content={contentType}/>
</head>
{body}
</html>
        out.println(html.toString)
        out.close
    }

    /**
     * Build the actual installer jar.
     */
    private def installerAction =
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
                       ("*.jar" - "izpack*.jar" - "scalatest*.jar" -
                        "ocutil*.jar")
            val jarDirPath = Path.fromFile(jarDir)
            log.info("Copying jars to \"" + jarDir + "\"")
            FileUtilities.copyFlat(jars.get, jarDirPath, log)

            // Create the IzPack configuration file from the template.

            log.info("Creating IzPack configuration file.")
            val temp = File.createTempFile("inst", ".xml")
            temp.deleteOnExit

            val vars = Map(
                "API_DOCS_DIR" -> ("target"/"doc"/"main"/"api").absolutePath,
                "DOCS_DIR" -> targetDocsDir.absolutePath,
                "JAR_FILE" -> jarPath.absolutePath,
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

            // Run IzPack. This is the simplest, least-coupled way to do it,
            // apparently.

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

    private def transformPaths(targetDir: Path, 
                               paths: PathFinder,
                               transform: (String) => String): Iterable[Path] =
    {
        val justFileNames = paths.get.map(p => p.asFile.basename.getPath)
        val transformedNames = justFileNames.map(s => transform(s))
        transformedNames.map(s => targetDir / s)
    }

    private def fileLines(path: Path): Iterator[String] =
        Source.fromFile(path.absolutePath).getLines
}
