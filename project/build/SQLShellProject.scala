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

    // Use the "##" base path construct to indicate that "target/classes"
    // must be stripped off before this file is packaged.
    val aboutInfoPath = ("target" / "classes" ##) / "org" / "clapper" /
                         "sqlshell" / "SQLShell.properties"
    val izPackHome = 
        if (System.getenv("IZPACK_HOME") != null)
            System.getenv("IZPACK_HOME")
        else
            pathFor(System.getProperty("user.home"), "java", "izPack")

    val sourceDocsDir = "src" / "docs"
    val targetDocsDir = "target" / "doc"
    val usersGuide = sourceDocsDir / "users-guide.md"
    val markdownFiles = (path(".") * "*.md") +++ usersGuide
    val markdownHtmlFiles = transformPaths(targetDocsDir,
                                           markdownFiles,
                                           {_.replaceAll("\\.md$", ".html")})
    val markdownSources = markdownFiles +++
                          (sourceDocsDir / "markdown.css") +++
                          (sourceDocsDir ** "*.js")

    // Add the "about" info file to the resources to be included in the jar
    override def mainResources = super.mainResources +++ aboutInfoPath

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
        markdown("README.md", targetDocsDir / "README.html", false)
        markdown("BUILDING.md", targetDocsDir / "BUILDING.html", false)
        markdown("LICENSE.md", targetDocsDir / "LICENSE.html", false)
        markdown(usersGuide, targetDocsDir / "users-guide.html", true)
        FileUtilities.copyFile(sourceDocsDir / "toc.js", 
                               targetDocsDir / "toc.js",
                               log)
        None
    } 
    .dependsOn(makeTargetDocsDir)

    // Copy Markdown sources into target/docs
    lazy val markdownDocs = copyTask(markdownFiles, targetDocsDir)

    // Local doc production
    lazy val targetDocs = task {None} dependsOn(htmlDocs, markdownDocs)

    // Override the "doc" action to depend on additional doc targets
    override def docAction = super.docAction dependsOn(targetDocs)

    // Dependency should point the other way, but overriding compileAction
    // somehow causes createAboutInfo to run BEFORE the clean action (when
    // clean is invoked).

    lazy val aboutInfo = task {createAboutInfo; None}
    override def compileAction = super.compileAction dependsOn(aboutInfo)

    // Console with project defs. Essentially sets up a "project-console"
    // alias for the built-in "console-project" task, because I can never
    // remember whether "console" or "project" comes first in the task's
    // name.
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
    val opencsv = "net.sf.opencsv" % "opencsv" % "1.8"

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
     * @param useToc  whether or not to include the table of contents
     */
    private def markdown(source: Path, target: Path, useToc: Boolean): Unit =
    {
        val javaVersion = system[String]("java.version").get.get
        if (javaVersion startsWith "1.6")
            throw new Exception("Java Markdown parser currently fails with " +
                                "1.6 Java")

        import java.io.{FileOutputStream, OutputStreamWriter, PrintWriter}
        import scala.xml.parsing.XhtmlParser

        val classpath = "lib_managed" / "compile" * "*.jar"
        val Encoding = "ISO-8859-1"

        import com.petebevin.markdown.MarkdownProcessor

        val md = new MarkdownProcessor
        log.info("Generating \"" + target + "\" from \"" + source + "\"")
        val cssLines = fileLines(sourceDocsDir / "markdown.css")
        val sourceLines = fileLines(source).toList
        val tocJavascriptSrc = if (useToc) "toc.js" else ""
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
<script type="text/javascript" src={tocJavascriptSrc}/>
<meta http-equiv="content-type" content={contentType}/>
</head>
<div id="body">
{body}
</div>
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

            val jars = (("lib" +++ "lib_managed") **
                        ("*.jar" - "izpack*.jar" - "scalatest*.jar" -
                         "ocutil*.jar")) +++
                       ("project" / "boot"  ** "scala-library.jar")
            println(jars.get)
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

    private def createAboutInfo: Unit =
    {
        import java.util.{Date, Properties}
        import java.text.SimpleDateFormat
        import java.io.{File, FileOutputStream}
        import _root_.grizzled.file.{util => FileUtil}

        val fullPath = aboutInfoPath.absolutePath
        log.info("Creating \"about\" properties in " + fullPath)
        val dir = new File(FileUtil.dirname(fullPath))
        if ((! dir.exists) && (! dir.mkdirs))
            throw new Exception("Can't create directory path: " + dir.getPath)

        val aboutProps = new Properties
        val formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        aboutProps.setProperty("sqlshell.name", projectName.value)
        aboutProps.setProperty("sqlshell.version", 
                               projectVersion.value.toString)
        aboutProps.setProperty("build.date", formatter.format(new Date))
        aboutProps.setProperty("build.compiler",
                               "Scala " + scalaVersion.value.toString)

        val osName = system[String]("os.name").get
        val osVersion = system[String]("os.version").get
        (osName, osVersion) match
        {
            case (Some(name), None) => 
                aboutProps.setProperty("build.os", name)
            case (Some(name), Some(version)) =>
                aboutProps.setProperty("build.os", name + " " + version)
            case _ =>
                aboutProps.setProperty("build.os", "unknown")
        }

        aboutProps.store(new FileOutputStream(fullPath),
                         "Automatically generated SQLShell build information")
    }
}
