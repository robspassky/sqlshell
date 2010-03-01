import sbt._

import scala.io.Source

import java.io.{File, FileWriter, PrintWriter}

import grizzled.file.implicits._

import org.clapper.sbtplugins.{EditSourcePlugin, MarkdownPlugin}

/**
 * To build SQLShell via SBT.
 */
class SQLShellProject(info: ProjectInfo)
    extends DefaultProject(info)
    with MarkdownPlugin
    with EditSourcePlugin
{
    /* ---------------------------------------------------------------------- *\
                         Compiler and SBT Options
    \* ---------------------------------------------------------------------- */

    override def compileOptions = Unchecked :: super.compileOptions.toList
    override def parallelExecution = true // why not?

    /* ---------------------------------------------------------------------- *\
                             Various settings
    \* ---------------------------------------------------------------------- */

    val LocalLibDir = "local_lib"

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

    val scalaVersionDir = "scala-" + buildScalaVersion

    /* ---------------------------------------------------------------------- *\
                       Managed External Dependencies

               NOTE: Additional dependencies are declared in
         project/plugins/Plugins.scala. (Declaring them there allows them
                       to be imported in this file.)
    \* ---------------------------------------------------------------------- */

    val scalaToolsRepo = "Scala-Tools Maven Repository" at 
        "http://scala-tools.org/repo-releases/"

    val newReleaseToolsRepository = "Scala Tools Repository" at
        "http://nexus.scala-tools.org/content/repositories/snapshots/"
    val scalatest = "org.scalatest" % "scalatest" %
        "1.0.1-for-scala-2.8.0.Beta1-with-test-interfaces-0.3-SNAPSHOT"

    val joptSimple = "net.sf.jopt-simple" % "jopt-simple" % "3.1"
    val jodaTime = "joda-time" % "joda-time" % "1.6"
    val izPack = "org.codehaus.izpack" % "izpack-standalone-compiler" % "4.3.1"
    val opencsv = "net.sf.opencsv" % "opencsv" % "1.8"

    val orgClapperRepo = "clapper.org Maven Repository" at
        "http://maven.clapper.org"
    val grizzled = "org.clapper" % "grizzled-scala" % "0.3.1"

    /* ---------------------------------------------------------------------- *\
                         Custom tasks and actions
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
        val markdownCSS = Some(sourceDocsDir / "markdown.css")
        def markdownWithTOC(src: Path, target: Path) =
            markdown(src, target, log, markdownCSS, Some("toc.js"))
        def markdownWithoutTOC(src: Path, target: Path) =
            markdown(src, target, log, markdownCSS, None)

        markdownWithoutTOC("README.md", targetDocsDir / "README.html")
        markdownWithoutTOC("BUILDING.md", targetDocsDir / "BUILDING.html")
        markdownWithoutTOC("LICENSE.md", targetDocsDir / "LICENSE.html")
        markdownWithTOC(usersGuide, targetDocsDir / "users-guide.html")
        copyFile("FAQ", targetDocsDir / "FAQ")
        copyFile(sourceDocsDir / "toc.js", targetDocsDir / "toc.js")
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

    override def disableCrossPaths = true

    override def updateAction = markdownUpdateAction
                                .dependsOn(super.updateAction)

    override def cleanLibAction = super.cleanAction 
                                       .dependsOn(localCleanLib)
                                       .dependsOn(markdownCleanLibAction)
    lazy val localCleanLib = task { doLocalCleanLib }

    /* ---------------------------------------------------------------------- *\
                          Private Helper Methods
    \* ---------------------------------------------------------------------- */

    private def pathFor(pieces: List[String]): String = 
        pieces mkString File.separator

    private def pathFor(pieces: String*): String = pathFor(pieces.toList)

    /**
     * Simplified front-end to FileUtilities.clean()
     */
    private def cleanDir(dir: Path) = FileUtilities.clean(dir, log)

    /**
     * Run Markdown to convert a source (Markdown) file to HTML.
     *
     * @param markdownSource  the path to the source file
     * @param targetHTML      the path to the output file
     * @param useToc          whether or not to include the table of contents
     */
    private def markdown(markdownSource: Path, 
                         targetHTML: Path, 
                         useToc: Boolean) =
    {
        // Use Rhino to run the Showdown (Javascript) Markdown converter.
        // MarkdownJ has issues and appears to be unmaintained.
        //
        // Showdown is here: http://attacklab.net/showdown/
        //
        // This code was adapted from various examples, including the one
        // at http://blog.notdot.net/2009/10/Server-side-JavaScript-with-Rhino

        import org.mozilla.javascript.{Context, Function}
        import java.io.{FileOutputStream, FileReader, OutputStreamWriter}
        import java.text.SimpleDateFormat
        import java.util.Date
        import scala.xml.parsing.XhtmlParser

        val Encoding = "ISO-8859-1"

        log.info("Generating \"" + targetHTML + "\" from \"" +
                 markdownSource + "\"")

        // Initialize the Javascript environment
        val ctx = Context.enter
        try
        {
            val scope = ctx.initStandardObjects

            // Open the Showdown script and evaluate it in the Javascript
            // context.

            val scriptPath = ShowdownLocal
            val showdownScript = loadFile(scriptPath)
            ctx.evaluateString(scope, showdownScript, "showdown", 1, null)

            // Instantiate a new Showdown converter.

            val converterCtor = ctx.evaluateString(scope, "Showdown.converter",
                                                   "converter", 1, null)
                                .asInstanceOf[Function]
            val converter = converterCtor.construct(ctx, scope, null)

            // Get the function to call.

            val makeHTML = converter.get("makeHtml", 
                                         converter).asInstanceOf[Function]

            // Load the markdown source into a string, and convert it to HTML.

            val markdownSourceLines = fileLines(markdownSource).toList
            val markdownSourceString = markdownSourceLines mkString ""
            val htmlBody = makeHTML.call(ctx, scope, converter,
                                         Array[Object](markdownSourceString))

            // Prepare the final HTML.

            val cssLines = fileLines(sourceDocsDir / "markdown.css")
            val css = cssLines mkString ""
            val tocFile = if (useToc) "toc.js" else "no-toc.js"

            // Title is first line.
            val title = markdownSourceLines.head

            // Can't parse the body into something that can be interpolated
            // unless it's inside a single element. So, stuff it inside a
            // <div>. Use the id "body", which is necessary for the table
            // of contents stuff to work.
            val sHTML = "<div id=\"body\">" + htmlBody.toString + "</div>"
            val body = XhtmlParser(Source.fromString(sHTML))
            val out = new PrintWriter(
                          new OutputStreamWriter(
                              new FileOutputStream(targetHTML.absolutePath), 
                              Encoding))
            val contentType = "text/html; charset=" + Encoding
            val html = 
<html>
<head>
<title>{title}</title>
<style type="text/css">
{css}
</style>
<script type="text/javascript" src={tocFile}/>
<meta http-equiv="content-type" content={contentType}/>
</head>
<body onLoad="createTOC()">
{body}
<hr/>
<i>Generated {new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date)}</i>
</body>
</html>
            out.println(html.toString)
            out.close
        }

        finally
        {
            Context.exit
        }
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

            val jars = 
                (("lib" +++ "lib_managed") **
                 ("*.jar" - "izpack*.jar"
                          - "scalatest*.jar"
                          - "scala-library*.jar"
                          - "scala-compiler.jar")) +++
                 ("project" / "boot" / scalaVersionDir ** "scala-library.jar")

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
                "TOP_DIR" -> path(".").absolutePath,
                "SCALA_VERSION" -> buildScalaVersion
            )

            editSourceToFile(Source.fromFile(installFile.absolutePath),
                             vars,
                             temp)

            // Run IzPack. This is the simplest, least-coupled way to do it,
            // apparently.

            log.info("Creating installer jar.")
            val izPath = "lib_managed" / "compile" * "*.jar"
            val args = List(temp.getPath,
                            "-h", izPackHome,
                            "-b", ".",
                            "-o", ("target"/"install.jar").absolutePath,
                            "-k", "standard")
            defaultRunner.run("com.izforge.izpack.compiler.Compiler", 
                              izPath.get,
                              args,
                              log)
        }
    }

    private def copyFile(source: Path, target: Path)
    {
        log.info("Copying \"" + source.toString + "\" to \"" + 
                 target.toString + "\"")
        FileUtilities.copyFile(source, target, log)
    }

    private def copyFile(source: File, target: File)
    {
        log.info("Copying \"" + source.getPath.toString + "\" to \"" + 
                 target.getPath.toString + "\"")
        FileUtilities.copyFile(source, target, log)
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
        Source.fromFile(new File(path.absolutePath)).getLines

    private def loadFile(path: Path): String =
        fileLines(path) mkString "\n"

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
        aboutProps.setProperty("build.timestamp", formatter.format(new Date))
        aboutProps.setProperty("build.compiler",
                               "Scala " + buildScalaVersions.value.toString)

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

    private def doLocalCleanLib: Option[String] =
    {
        if (ShowdownLocal.exists)
        {
            log.info("Deleting " + LocalLibDir);
            FileUtilities.clean(LocalLibDir, log);
        }

        None
    }
}
