package org.clapper.sqlshell

import grizzled.cmd._
import grizzled.readline.{ListCompleter, PathnameCompleter}
import grizzled.readline.Readline
import grizzled.readline.Readline.ReadlineType
import grizzled.readline.Readline.ReadlineType._
import grizzled.string.implicits._
import grizzled.string.WordWrapper
import grizzled.config.Configuration
import grizzled.math.util._

import java.sql.{Connection,
                 DatabaseMetaData,
                 ResultSet,
                 ResultSetMetaData,
                 Statement,
                 SQLException,
                 Types => SQLTypes}
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat

import scala.collection.mutable.{Map => MutableMap}
import scala.io.Source
import scala.util.matching.Regex

/**
 * Constants that identify the name, version, copyright, etc., of this tool.
 */
object Ident
{
    val Version = "0.1"
    val Name = "sqlshell"
    val Copyright = "Copyright (c) 2009 Brian M. Clapper. All rights reserved."

    val IdentString = "%s, version %s\n%s" format (Name, Version, Copyright)
}

trait Wrapper
{
    val wordWrapper = new WordWrapper

    def wrapPrintln(s: String) = println(wordWrapper.wrap(s))

    def wrapPrintf(fmt: String, args: Any*) =
        println(wordWrapper.wrap(fmt format(args: _*)))
}

/**
 * Wraps a source in something that can be pushed onto the command
 * interpreter's reader stack.
 */
private class SourceReader(source: Source)
{
    // lines iterator
    private val itLines = source.getLines

    /**
     * Read the next line of input.
     *
     * @param prompt  the prompt (ignored)
     *
     * @return <tt>Some(line)</tt> with the next input line, or <tt>None</tt>
     *         on EOF.
     */
    def readline(prompt: String): Option[String] =
        if (itLines.hasNext)
            Some(itLines.next.chomp)
        else
            None
}

/**
 * Holds information about a table
 */
class TableSpec(val name: Option[String],
                val schema: Option[String],
                val tableType: Option[String])

/**
 * "Holds" the max history value
 */
class MaxHistorySetting(readline: Readline)
    extends Setting with IntValueConverter
{
    override def get = readline.history.max

    override def set(newValue: Any) =
        readline.history.max = newValue.asInstanceOf[Int]
}

/**
 * The actual class that implements the command line interpreter.
 *
 * @param config          the loaded configuration file
 * @param dbInfo          the information about the database to which to connect
 * @param readlineLibs    the list of readline libraries to try, in order; or
 *                        <tt>Nil</tt> to use the default set.
 * @param useAnsiColors   <tt>true</tt> to use ANSI terminal colors in various
 *                        output, <tt>false</tt> to avoid them
 * @param showStackTraces whether or not to show stack traces on error
 * @param beVerbose       whether or not to enable verbose messages
 * @param fileToRun       a file to run (then exit), or none for interactive
 *                        mode
 */
class SQLShell(val config: Configuration,
               dbInfo: DatabaseInfo,
               readlineLibs: List[ReadlineType],
               useAnsiColors: Boolean,
               showStackTraces: Boolean,
               beVerbose: Boolean,
               fileToRun: Option[File])
    extends CommandInterpreter("sqlshell", readlineLibs)
    with Wrapper with Sorter
{
    val connector = new DatabaseConnector(config)
    val connectionInfo = connector.connect(dbInfo)
    val connection = connectionInfo.connection
    val historyPath = connectionInfo.configInfo.get("history")

    val settings = new Settings(
        ("ansi",         new BooleanSetting(useAnsiColors)),
        ("echo",         new BooleanSetting(false)),
        ("maxhistory",   new MaxHistorySetting(readline)),
        ("schema",       new StringSetting("")),
        ("showbinary",   new IntSetting(0)),
        ("showrowcount", new BooleanSetting(true)),
        ("showtimings",  new BooleanSetting(true)),
        ("stacktrace",   new BooleanSetting(showStackTraces)),
        ("verbose",      new BooleanSetting(beVerbose))
    )

    // List of command handlers.

    val aboutHandler = new AboutHandler(this)
    val setHandler = new SetHandler(this)
    val transactionManager = new TransactionManager(this, connection)
    val handlers = transactionManager.handlers ++
                   List(new HistoryHandler(this),
                        new RedoHandler(this),
                        new RunFileHandler(this),

                        new SelectHandler(this, connection),
                        new InsertHandler(this, connection),
                        new DeleteHandler(this, connection),
                        new UpdateHandler(this, connection),
                        new CreateHandler(this, connection),
                        new AlterHandler(this, connection),
                        new DropHandler(this, connection),

                        new ShowHandler(this, connection),
                        new DescribeHandler(this, connection,
                                            transactionManager),
                        setHandler,
                        new EchoHandler,
                        new ExitHandler,
                        aboutHandler)

    loadSettings(config, connectionInfo)
    aboutHandler.showAbbreviatedInfo

    if (fileToRun != None)
    {
        // Push a reader for the file on the stack. To ensure that we don't
        // fall through to interactive mode, make sure there's an "exit" at
        // the end.

        pushReader(new SourceReader(Source.fromString("exit\n")).readline)
        pushReader(new SourceReader(Source.fromFile(fileToRun.get)).readline)
    }

    private val unknownHandler = new UnknownHandler(this, connection)

    // Allow "." characters in commands.
    override def StartCommandIdentifier = super.StartCommandIdentifier + "."

    override def error(message: String) =
        if (settings.booleanSettingIsTrue("ansi"))
            println(Console.RED + "Error: " + message + Console.RESET)
        else
            println("Error: " + message)

    override def warning(message: String) =
        if (settings.booleanSettingIsTrue("ansi"))
            println(Console.YELLOW + "Warning: " + message + Console.RESET)
        else
            println("Warning: " + message)

    def verbose(message: String) =
        if (settings.booleanSettingIsTrue("verbose"))
        {
            if (settings.booleanSettingIsTrue("ansi"))
                println(Console.BLUE + Console.BOLD + message + Console.RESET)
            else
                println(message)
        }

    override def preLoop: Unit =
    {
        historyPath match
        {
            case None =>
                return

            case Some(path) =>
                verbose("Loading history from \"" + path + "\"...")
                history.load(path)
        }
    }

    override def postLoop: Unit =
    {
        if (transactionManager.inTransaction)
        {
            warning("An uncommitted transaction is open. Rolling it back.")
            transactionManager.rollback()
        }

        historyPath match
        {
            case None =>
                return

            case Some(path) =>
                verbose("Saving history to \"" + path + "\"...")
                history.save(path)
        }
    }

    override def handleEOF: CommandAction =
    {
        verbose("EOF. Exiting.")
        println()
        Stop
    }

    override def handleUnknownCommand(commandName: String,
                                      unparsedArgs: String): CommandAction =
    {
        unknownHandler.runCommand(commandName, unparsedArgs)
        KeepGoing
    }

    override def handleException(e: Exception): CommandAction =
    {
        val message = if (e.getMessage == null)
                          e.getClass.getName
                      else
                          e.getMessage

        verbose("Caught exception.")
        error(message)
        if (settings.booleanSettingIsTrue("stacktrace"))
            e.printStackTrace(System.out)

        KeepGoing
    }

    override def preCommand(line: String) =
    {
        if (line.ltrim.startsWith("--"))
            Some("")

        else
        {
            if (settings.booleanSettingIsTrue("echo"))
                printf("\n>>> %s\n\n", line)
            Some(line)
        }
    }

    /**
     * Take a string (which may be null) representing a schema name and
     * return a schema name. If the schema is null, then the default schema
     * is returned. If there is no default schema, then <tt>None</tt> is
     * returned and an error is printed.
     *
     * @param schemaName  the schema name (or null)
     *
     * @return <tt>Some(name)</tt> or <tt>None</tt>
     */
    private[sqlshell] def getSchema(schemaString: String): Option[String] =
    {
        val schemaOpt = schemaString match
        {
            case null => None
            case s    => Some(s)
        }

        getSchema(schemaOpt)
    }

    /**
     * Take a string option (which may be <tt>None</tt>) representing a
     * schema name and return a schema name. If the schema is
     * <tt>None</tt>, then the default schema is returned. If there is no
     * default schema, then <tt>None</tt> is returned and an error is
     * printed.
     *
     * @param schemaName  the schema name (or <tt>None</tt>)
     *
     * @return <tt>Some(name)</tt> or <tt>None</tt>
     */
    private[sqlshell] def getSchema(schema: Option[String]): Option[String] =
    {
        val actualSchema = schema match
        {
            case None =>
                settings.stringSetting("schema")
            case Some(s) =>
                Some(s)
        }

        if (actualSchema == None)
            wrapPrintln("No schema specified, and no default schema set. " +
                        "To set a default schema, use:\n\n" +
                        ".set schema schemaName\n\n" +
                        "Use a schema name of * for all schemas.")

        actualSchema
    }

    /**
     * Get the table names that match a regular expression filter.
     *
     * @param schema      schema to use, or None for the default
     * @param nameFilter  regular expression to use to filter the names
     *
     * @return a list of matching tables, or Nil
     */
    private[sqlshell] def getTables(schema: Option[String],
                                    nameFilter: Regex): List[TableSpec] =
    {
        def toOption(s: String): Option[String] =
            if (s == null) None else Some(s)

        def getTableData(rs: ResultSet): List[TableSpec] =
        {
            if (! rs.next)
                Nil

            else
                new TableSpec(toOption(rs.getString("TABLE_NAME")),
                              toOption(rs.getString("TABLE_SCHEM")),
                              toOption(rs.getString("TABLE_TYPE"))) ::
                getTableData(rs)
        }

        getSchema(schema) match
        {
            case None =>
                // Error already reported.
                Nil

            case Some(schema) =>
                val metadata = connection.getMetaData
                val rs = metadata.getTables(null, schema, null,
                                            Array("TABLE", "VIEW"))
                try
                {
                    def matches(ts: TableSpec): Boolean =
                        (ts != None) &&
                        (nameFilter.findFirstIn(ts.name.get) != None)

                    getTableData(rs).filter(matches(_))
                }

                finally
                {
                    rs.close
                }
        }
    }

    /**
     * Get all table data.
     *
     * @param schema      schema to use, or None for the default
     *
     * @return a list of matching tables, or Nil
     */
    private[sqlshell] def getTables(schema: Option[String]): List[TableSpec] =
        getTables(schema, """.*""".r)

    /**
     * Get all table data.
     *
     * @param schema      schema to use; "" or null for the default
     *
     * @return a list of matching tables, or Nil
     */
    private[sqlshell] def getTables(schema: String): List[TableSpec] =
    {
        val schemaOpt = if ((schema == null) || (schema.trim == "")) None
                        else Some(schema)
        getTables(schemaOpt, """.*""".r)
    }

    /**
     * Get the names of all tables in a given schema.
     *
     * @param schema the schema to use, or None for the default
     *
     * @return a sorted list of table names
     */
    private[sqlshell] def getTableNames(schema: Option[String]): List[String] =
    {
        val tableData = getTables(schema)
        val tableNames = tableData.filter(_.name != None).map(_.name.get)
        tableNames.sort(nameSorter)
    }

    /**
     * Get the names of all schemas in the database.
     *
     * @return a list of schema names
     */
    private[sqlshell] def getSchemas: List[String] =
    {
        def getResults(rs: ResultSet): List[String] =
        {
            if (! rs.next)
                Nil

            else
                rs.getString(1) :: getResults(rs)
        }

        val metadata = connection.getMetaData
        val rs = metadata.getSchemas
        try
        {
            getResults(rs)
        }

        finally
        {
            rs.close
        }
    }

    private def loadSettings(config: Configuration,
                             connectionInfo: ConnectionInfo) =
    {
        if (config.hasSection("settings"))
        {
            verbose("Loading settings from configuration.")
            for ((variable, value) <- config.options("settings"))
                try
                {
                    settings.changeSetting(variable, value)
                    verbose("+ " + setHandler.CommandName + " " +
                            variable + "=" + value)
                }
                catch
                {
                    case e: UnknownVariableException => warning(e.message)
                }
        }

        connectionInfo.configInfo.get("schema") match
        {
            case None =>

            case Some(schema) =>
                settings.changeSetting("schema", schema)
                verbose("+ " + setHandler.CommandName + " schema=" + schema)
        }
    }
}

/**
 * Timer mix-in.
 */
trait Timer
{
    import org.joda.time.Period
    import org.joda.time.format.PeriodFormatterBuilder

    private val formatter = new PeriodFormatterBuilder()
                                .printZeroAlways
                                .appendSeconds
                                .appendSeparator(".")
                                .appendMillis
                                .appendSuffix(" second", " seconds")
                                .toFormatter

    /**
     * Takes a fragment of code, executes it, and returns how long it took
     * (in real time) to execute.
     *
     * @param block  block of code to run
     *
     * @return a (time, result) tuple, consisting of the number of
     * milliseconds it took to run (time) and the result from the block.
     */
    def time[T](block: => T): (Long, T) =
    {
        val start = System.currentTimeMillis
        val result = block
        (System.currentTimeMillis - start, result)
    }

    /**
     * Format the value of the period between two times, returning the
     * string.
     *
     * @param elapsed  the (already subtracted) elapsed time, in milliseconds
     *
     * @return the string
     */
    def formatInterval(elapsed: Long): String =
    {
        val buf = new StringBuffer
        formatter.printTo(buf, new Period(elapsed))
        buf.toString
    }

    /**
     * Format the value of the interval between two times, returning the
     * string.
     *
     * @param start   start time, as milliseconds from the epoch
     * @param end     end time, as milliseconds from the epoch
     *
     * @return the string
     */
    def formatInterval(start: Long, end: Long): String =
        formatInterval(end - start)
}

abstract class SQLShellCommandHandler extends CommandHandler
{
    def runCommand(commandName: String, args: String): CommandAction =
        doRunCommand(commandName, removeSemicolon(args))

    def doRunCommand(commandName: String, args: String): CommandAction

    protected def removeSemicolon(s: String): String =
        if (s endsWith ";")
            s.rtrim.substring(0, s.length - 1)
        else
            s
}

/**
 * Handles the "exit" command
 */
class ExitHandler extends SQLShellCommandHandler
{
    val CommandName = "exit"
    val Help = "Exit SQLShell."

    def doRunCommand(commandName: String, args: String): CommandAction = Stop
}

/**
 * Handles the ".echo" command
 */
class EchoHandler extends SQLShellCommandHandler
{
    val CommandName = ".echo"
    val Help = """|Echo the remaining arguments to the terminal. For example:
                  |
                  |     .echo This will be displayed.""".stripMargin

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        println(args)
        KeepGoing
    }
}

/**
 * Handles the ".run" command
 */
class RunFileHandler(val shell: SQLShell) extends SQLShellCommandHandler
{
    val CommandName = ".run"
    val Help = """|Load and run the contents of the specified file.
                  |
                  |     .run path""".stripMargin

    private val completer = new PathnameCompleter

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        args.tokenize match
        {
            case Nil =>
                error("You must specify a file to be run.")
            case file :: Nil =>
                val reader = new SourceReader(Source.fromFile(file))
                shell.verbose("Loading and running \"" + file + "\"")
                shell.pushReader(reader.readline)
            case _ =>
                error("Too many parameters to " + CommandName + " command.")
        }

        KeepGoing
    }

    override def complete(token: String, line: String): List[String] =
        completer.complete(token, line)
}

/**
 * Handles the ".set" command.
 */
class SetHandler(val shell: SQLShell) extends SQLShellCommandHandler with Sorter
{
    val CommandName = ".set"
    val Help = """Change one of the SQLShell settings. Usage:
                 |
                 |    .set            -- show the current settings
                 |    .set var value  -- change a variable
                 |    .set var=value  -- change a variable""".stripMargin

    val variables = shell.settings.variableNames
    val completer = new ListCompleter(variables)

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        val trimmedArgs = args.trim
        if (trimmedArgs == "")
            showSettings

        else
        {
            val i = args.indexOf('=')
            val chopAt = if (i >= 0) i else args.indexOf(' ')
            try
            {
                if (chopAt < 0)
                {
                    val variable = args.trim
                    val value = shell.settings(variable)
                    printf("%s: %s\n", variable, value.toString)
                }

                else
                {
                    val variable = args.substring(0, chopAt).trim
                    val value = args.substring(chopAt + 1).trim
                    val strippedValue = stripQuotes(value)
                    shell.settings.changeSetting(variable, strippedValue)
                }
            }

            catch
            {
                case e: UnknownVariableException => shell.warning(e.message)
            }
        }

        KeepGoing
    }

    override def complete(token: String, line: String): List[String] =
        completer.complete(token, line)

    private def stripQuotes(s: String): String =
    {
        val ch = s(0)
        if ("\"'" contains ch)
        {
            if (s.length == 1)
                throw new SQLShellException("Unbalanced quotes in value: " + s)

            if (s.last != ch)
                throw new SQLShellException("Unbalanced quotes in value: " + s)

            s.substring(1, s.length - 1)
        }

        else
            s
    }

    private def showSettings =
    {
        val varWidth = max(variables.map(_.length): _*)
        val fmt = "%" + varWidth + "s: %s"

        for (variable <- variables)
        {
            val value = shell.settings(variable).toString
            println(fmt format(variable, value))
        }
    }
}

/**
 * Handles the ".about" command.
 */
class AboutHandler(val shell: SQLShell) extends SQLShellCommandHandler with Sorter
{
    import java.util.Properties

    val CommandName = ".about"
    val Help = "Display information about SQLShell"

    private val buildInfo: Properties = loadBuildInfo

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        showFullInfo
        KeepGoing
    }

    def showAbbreviatedInfo =
    {
        println(Ident.IdentString)
        println("Using " + shell.readline + " readline implementation.")
    }

    def showFullInfo =
    {
        showAbbreviatedInfo
        val buildDate = buildInfo.getProperty("build.date")
        val compiler = buildInfo.getProperty("build.compiler")
        val buildOS = buildInfo.getProperty("build.os")
        if (buildDate != null)
            println("Build date: " + buildDate);
        if (compiler != null)
            println("Built with: " + compiler);
        if (buildOS != null)
            println("Build OS:   " + buildOS);
    }

    private def loadBuildInfo =
    {
        val classLoader = getClass.getClassLoader
        val BuildInfoURL = classLoader.getResource(
            "org/clapper/sqlshell/BuildInfo.properties")

        val buildInfo = new Properties
        if (BuildInfoURL != null)
        {
            try
            {
                val is = BuildInfoURL.openStream
                try
                {
                    buildInfo.load(is)
                }

                finally
                {
                    is.close
                }
            }

            catch
            {
                case e: Throwable =>
                    shell.warning("Can't load " + BuildInfoURL + ": " +
                                  e.getMessage)
            }
        }

        buildInfo
    }

}

/**
 * Helpful JDBC routines.
 */
trait JDBCHelper
{
    protected def withSQLStatement(connection: Connection)
                                  (code: (Statement) => Unit) =
    {
        val statement = connection.createStatement
        try
        {
            code(statement)
        }

        finally
        {
            statement.close
        }
    }

    protected def withResultSet(rs: ResultSet)(code: => Unit) =
    {
        try
        {
            code
        }

        finally
        {
            rs.close
        }
    }
}

abstract class SQLHandler(val shell: SQLShell, val connection: Connection)
    extends SQLShellCommandHandler with Timer with JDBCHelper
{
    override def moreInputNeeded(lineSoFar: String): Boolean =
        (! lineSoFar.ltrim.endsWith(";"))

    override def complete(token: String, line: String): List[String] =
    {
        // Allocate a new completer each time, because the table names can
        // change between invocations of this method.

        new ListCompleter(shell.getTableNames(None), _.toLowerCase).
            complete(token, line)
    }
}

/**
 * Handles SQL "SELECT" statements.
 */
class SelectHandler(shell: SQLShell, connection: Connection)
    extends SQLHandler(shell, connection) with Timer
{
    import java.io.{EOFException,
                    FileInputStream,
                    FileOutputStream,
                    InputStream,
                    ObjectInputStream,
                    ObjectOutputStream,
                    Reader}

    class PreprocessedResults(val rowCount: Int,
                              val columnNamesAndSizes: Map[String, Int],
                              val dataFile: File)

    val DateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S")
    val CommandName = "select"
    val Help = """Issue a SQL SELECT statement and display the results"""
    val ColumnSeparator = "  "

    val tempFile = File.createTempFile("sqlshell", ".dat")
    tempFile.deleteOnExit

    /**
     * Run the "SELECT" command and dump the output.
     *
     * @param commandName the command (i.e., "select")
     * @param args        the remainder of the SELECT command
     *
     * @return KeepGoing, always
     */
    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        val newArgs = removeSemicolon(args)
        withSQLStatement(connection)
        {
            statement =>

            val (elapsed, rs) =
                time[ResultSet]
                {
                    statement.executeQuery(commandName + " " + newArgs)
                }

            withResultSet(rs)
            {
                dumpResults(elapsed, rs)
            }
        }

        KeepGoing
    }

    /**
     * Dump the results of a query.
     *
     * @param queryTime  the number of milliseconds the query took to run;
     *                   displayed with the results
     * @param rs         the JDBC result set
     */
    protected def dumpResults(queryTime: Long, rs: ResultSet): Unit =
    {
        shell.verbose("Processing results...")

        val (preprocessingTime, preprocessedResults) =
            time[PreprocessedResults]
            {
                preprocess(rs)
            }

        if (shell.settings.booleanSettingIsTrue("showrowcount"))
        {
            if (preprocessedResults.rowCount == 0)
                println("No rows returned.")
            else if (preprocessedResults.rowCount == 1)
                println("1 row returned.")
            else
                printf("%d rows returned.\n", preprocessedResults.rowCount)
        }

        if (shell.settings.booleanSettingIsTrue("showtimings"))
        {
            println("Execution time: " + formatInterval(queryTime))
            println("Retrieval time: " + formatInterval(preprocessingTime))
        }

        // Note: Scala's format method doesn't left-justify.
        def formatter = new java.util.Formatter

        // Print column names...
        val colNamesAndSizes = preprocessedResults.columnNamesAndSizes
        val columnNames = colNamesAndSizes.keys.toList
        val columnFormats =
            Map.empty[String, String] ++
            (columnNames.map(col => (col, "%-" + colNamesAndSizes(col) + "s")))

        println()
        println(
            {for (col <- columnNames)
                 yield formatter.format(columnFormats(col), col)}
            .toList
            .mkString(ColumnSeparator)
        )

        // ...and a separator.
        println(
            {for {col <- columnNames
                  size = colNamesAndSizes(col)}
                 yield formatter.format(columnFormats(col), "-" * size)}
            .toList
            .mkString(ColumnSeparator)
        )

        // Now, load the serialized results and dump them.
        val rowsIn = new ObjectInputStream(
            new FileInputStream(preprocessedResults.dataFile)
        )

        def dumpNextRow: Unit =
        {
            try
            {
                val rowMap = rowsIn.readObject.asInstanceOf[Map[String, String]]
                val data =
                    {for {col <- columnNames
                          size = colNamesAndSizes(col)
                          fmt = columnFormats(col)}
                         yield formatter.format(fmt, rowMap(col))}.toList
                println(data mkString ColumnSeparator)

                dumpNextRow
            }

            catch
            {
                case _: EOFException =>
            }
        }

        dumpNextRow
    }

    /**
     * Preprocess the result set. The result set is read completely and
     * serialized to a file, allowing the number of rows to be counted,
     * the maximum size of each column of output to be determined, etc.
     *
     * @param rs  the result set
     *
     * @return a PreprocessedResults object containing the results
     */
    private def preprocess(rs: ResultSet): PreprocessedResults =
    {
        import scala.collection.mutable.LinkedHashMap

        val tempOut = new ObjectOutputStream(new FileOutputStream(tempFile))
        try
        {
            val colNamesAndSizes = new LinkedHashMap[String, Int]

            // Get the column names.

            val metadata = rs.getMetaData
            for {i <- 1 to metadata.getColumnCount
                 name = metadata.getColumnName(i)}
                colNamesAndSizes += (name -> name.length)

            // Serialize the results to a file. This allows counting
            // the rows ahead of time.

            def preprocessResultRow(rs: ResultSet, lastRowNum: Int): Int =
            {
                if (! rs.next)
                    lastRowNum

                else
                {
                    val mappedRow = mapRow(rs, metadata)
                    tempOut.writeObject(mappedRow)

                    for ((colName, value) <- mappedRow)
                    {
                        val size = value.length
                        val max = Math.max(colNamesAndSizes(colName), size)
                        colNamesAndSizes(colName) = max
                    }

                    preprocessResultRow(rs, lastRowNum + 1)
                }
            }

            shell.verbose("Preprocessing result set...")
            val rows = preprocessResultRow(rs, 0)
            new PreprocessedResults(rows, 
                                    Map.empty[String, Int] ++ colNamesAndSizes,
                                    tempFile)
        }

        finally
        {
            tempOut.close
        }
    }

    /**
     * Map a result set row, returning the each column and its name.
     *
     * @param rs       the result set
     * @param metadata the result set's metadata
     *
     * @return a map of (columnName, columnValue) string pairs
     */
    private def mapRow(rs: ResultSet,
                       metadata: ResultSetMetaData): Map[String, String] =
    {
        import grizzled.io.implicits._   // for the readSome() method

        def getDateString(date: Date): String = DateFormatter.format(date)

        def clobString(i: Int): String =
        {
            val binaryLength = shell.settings.intSetting("showbinary")
            if (binaryLength == 0)
                "<clob>"

            else
            {
                val r = rs.getCharacterStream(i)

                try
                {
                    // Read one more than the binary length. If we get that
                    // many, then display an ellipsis.

                    val buf = r.readSome(binaryLength + 1)
                    buf.length match
                    {
                        case 0 =>                       ""
                        case n if (n > binaryLength) => buf.mkString("") + "..."
                        case n =>                       buf.mkString("")
                    }
                }

                finally
                {
                    r.close
                }
            }
        }

        def binaryString(i: Int): String =
        {
            val binaryLength = shell.settings.intSetting("showbinary")
            if (binaryLength == 0)
                "<blob>"

            else
            {
                val is = rs.getBinaryStream(i)

                try
                {
                    // Read one more than the binary length. If we get that
                    // many, then display an ellipsis.

                    val buf = is.readSome(binaryLength + 1)
                    val ellipsis = buf.length > binaryLength
                    val hexList =
                    {
                        for (b <- buf)
                            yield b.asInstanceOf[Int].toHexString.toList match
                            {
                                case digit :: Nil => "0" + digit
                                case digits       => digits mkString ""
                            }
                    }
                    val hexString = hexList.mkString("")
                    if (ellipsis) hexString + "..." else hexString
                }

                finally
                {
                    is.close
                }
            }
        }

        def nonNullColAsString(i: Int): String =
        {
            metadata.getColumnType(i) match
            {
                // Handle just the oddballs. getObject(i).toString should be
                // sufficient to handle the other column types.

                case SQLTypes.BINARY        => binaryString(i)
                case SQLTypes.BLOB          => binaryString(i)
                case SQLTypes.CLOB          => clobString(i)
                case SQLTypes.DATE          => getDateString(rs.getDate(i))
                case SQLTypes.LONGVARBINARY => binaryString(i)
                case SQLTypes.LONGVARCHAR   => clobString(i)
                case SQLTypes.NULL          => "<null>"
                case SQLTypes.TIME          => getDateString(rs.getTime(i))
                case SQLTypes.TIMESTAMP     => getDateString(rs.getTimestamp(i))
                case SQLTypes.VARBINARY     => binaryString(i)
                case _                      => rs.getObject(i).toString
            }
        }

        def colAsString(i: Int): String =
        {
            rs.getObject(i)
            if (rs.wasNull) "NULL" else nonNullColAsString(i)
        }

        Map.empty[String, String] ++
            {for {i <- 1 to metadata.getColumnCount
                  colName = metadata.getColumnName(i)}
                 yield (colName, colAsString(i))}.toList
    }
}

/**
 * Handles any command that calls Statement.executeUpdate(). This class
 * is abstract so that specific handlers can be instantiated for individual
 * commands (allowing individual help).
 */
abstract class AnyUpdateHandler(shell: SQLShell, connection: Connection)
    extends SQLHandler(shell, connection) with Timer
{
    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        val newArgs = removeSemicolon(args)
        withSQLStatement(connection)
        {
            statement =>

            val (elapsed, rows) =
                time[Int]
                {
                    statement.executeUpdate(commandName + " " + newArgs)
                }

            if (shell.settings.booleanSettingIsTrue("showrowcount"))
            {
                if (rows == 0)
                    println("No rows affected.")
                else if (rows == 1)
                    println("1 row affected.")
                else
                    printf("%d rows affected.\n", rows)
            }

            if (shell.settings.booleanSettingIsTrue("showtimings"))
                println("Execution time: " + formatInterval(elapsed))
        }

        KeepGoing
    }
}

/**
 * Handles a SQL "UPDATE" command.
 */
class UpdateHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "update"
    val Help = """Issue a SQL UPDATE statement."""
}

/**
 * Handles a SQL "INSERT" command.
 */
class InsertHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "insert"
    val Help = """Issue a SQL INSERT statement."""
}

/**
 * Handles a SQL "DELETE" command.
 */
class DeleteHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "delete"
    val Help = """Issue a SQL DELETE statement."""
}

/**
 * Handles a SQL "ALTER" command.
 */
class AlterHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "alter"
    val Help = """Issue a SQL ALTER statement."""
}

/**
 * Handles a SQL "CREATE" command.
 */
class CreateHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "create"
    val Help = """Issue a SQL CREATE statement."""
}

/**
 * Handles a SQL "DROP" command.
 */
class DropHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "drop"
    val Help = """Issue a SQL DROP statement."""
}

/**
 * Contains handlers and state for transaction management.
 */
class TransactionManager(val shell: SQLShell, val connection: Connection)
{
    /**
     * Determines whether a transaction is open or not. If a transaction
     * is open, autocommit will be off. Otherwise, it will be on.
     *
     * @return <tt>true</tt> if a transaction is open (i.e., "BEGIN"
     *         was invoked, without yet seeing <tt>COMMIT</tt> or
     *         <tt>ROLLBACK</tt>; <tt>false</tt> if not.
     */
    def inTransaction: Boolean =
    {
        try
        {
            ! connection.getAutoCommit
        }

        catch
        {
            case e: SQLException =>
                shell.error("Cannot determine autocommit status: " +
                            e.getMessage)
                false
        }
    }

    /**
     * Commit a transaction.
     */
    def commit(): Unit =
    {
        if (! inTransaction)
            shell.warning("Not in a transaction. Commit ignored.")

        else
        {
            connection.commit()
            setAutoCommit(true)
        }
    }

    /**
     * Roll a transaction back.
     */
    def rollback(): Unit =
    {
        if (! inTransaction)
            shell.warning("Not in a transaction. Rollback ignored.")

        else
        {
            connection.rollback()
            setAutoCommit(true)
        }
    }

    private def setAutoCommit(onOff: Boolean) =
    {
        try
        {
            connection.setAutoCommit(onOff)
        }

        catch
        {
            case e: SQLException =>
                shell.error("Cannot change autocommit status: " +
                            e.getMessage)
        }
    }

    trait NoArgChecker
    {
        protected def checkForNoArgs(commandName: String, args: String) =
        {
            if (args.trim != "")
                shell.warning("Ignoring arguments to " + commandName +
                              " command.")
        }
    }

    /**
     * Handles a SQL "BEGIN" pseudo-command.
     */
    object BeginHandler extends SQLShellCommandHandler with NoArgChecker
    {
        val CommandName = "begin"
        val Help =
"""|Start a new transaction. BEGIN switches disables autocommit for the
   |database connection. Any SQL updates to the database occur within a
   |transaction that must either be committed or rolled back. (See the "commit"
   |and "rollback" commands.)""".stripMargin

        def doRunCommand(commandName: String, args: String): CommandAction =
        {
            checkForNoArgs(commandName, args)
            if (inTransaction)
                shell.warning("Already in a transaction. BEGIN ignored.")
            else
                setAutoCommit(false)

            KeepGoing
        }
    }

    /**
     * Handles a SQL "COMMIT" command.
     */
    object CommitHandler extends SQLShellCommandHandler with NoArgChecker
    {
        val CommandName = "commit"
        val Help =
"""|Commits a transaction. This command is only within a transaction (that is,
   |if "begin" has been issued, but neither "commit" nor "rollback" has yet
   |issued. (See the "begin" and "rollback" commands.)""".stripMargin

        def doRunCommand(commandName: String, args: String): CommandAction =
        {
            checkForNoArgs(commandName, args)
            commit()
            KeepGoing
        }
    }

    /**
     * Handles a SQL "ROLLBACK" command.
     */
    object RollbackHandler extends SQLShellCommandHandler with NoArgChecker
    {
        val CommandName = "rollback"
        val Help =
"""|Rolls a transaction back. This command is only within a transaction (that
   |is, if "begin" has been issued, but neither "commit" nor "rollback" has yet
   |issued. (See the "begin" and "commit" commands.)""".stripMargin

        def doRunCommand(commandName: String, args: String): CommandAction =
        {
            checkForNoArgs(commandName, args)
            rollback()
            KeepGoing
        }
    }

    /**
     * Gets all the handlers supplied by this class.
     */
    val handlers = List(BeginHandler, CommitHandler, RollbackHandler)
}

/**
 * Handles any unknown command.
 */
class UnknownHandler(shell: SQLShell, connection: Connection)
    extends SQLShellCommandHandler
{
    val CommandName = ""
    val Help = """Issue an unknown SQL statement."""

    val updateHandler = new UpdateHandler(shell, connection)
    val queryHandler = new SelectHandler(shell, connection)

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        // Try it as both a query and an update.

        try
        {
            queryHandler.runCommand(commandName, args)
        }

        catch
        {
            case _ => updateHandler.runCommand(commandName, args)
        }

        KeepGoing
    }
}

class DescribeHandler(val shell: SQLShell,
                      val connection: Connection,
                      val transactionManager: TransactionManager)
    extends SQLShellCommandHandler
    with Wrapper with JDBCHelper with Sorter
{
    val CommandName = ".desc"
    val Help =
"""|Describe database objects.
   |
   |.desc database
   |    Show information about the database, including the database vendor,
   |    the JDBC driver, and whether or not a transaction is currently open.
   |
   |.desc table [full]
   |
   |    Describe a table, showing the column names and their types. If "full"
   |    is specified, show the indexes and constraints, as well.
   |
   |    In the first form of the command, the schema name identifies the schema
   |    in which to find the table. In the second form of the command, the
   |    schema is taken from the default schema.
   |
   |    See ".set schema" for more information.""".stripMargin

    private val jdbcTypeNames = getJdbcTypeNames

    private val subCommands = List("database")
    private def subCommandCompleter =
    {
        val tables = shell.getTableNames(None)
        new ListCompleter(subCommands ++ tables, _.toLowerCase)
    }

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        args.tokenize match
        {
            case "database" :: stuff :: Nil =>
                describeDatabase
            case "database" :: Nil =>
                describeDatabase
            case table :: "full" :: Nil =>
                describeTable(table, true)
            case table :: Nil =>
                describeTable(table, false)
            case Nil =>
                shell.error("Missing the object to describe.")
            case _ =>
                shell.error("Bad .desc command: \"" + args + "\"")
        }

        KeepGoing
    }

    override def complete(token: String, line: String): List[String] =
    {
        line.tokenize match
        {
            case Nil =>
                assert(false) // shouldn't happen
                Nil

            case cmd :: Nil =>
                // Command filled in (obviously, or we wouldn't be in here),
                // but first argument not.
                subCommandCompleter.complete(token, line)

            case cmd :: s :: Nil if (s == "database") =>
                Nil

            case cmd :: s :: Nil if (s.endsWith(" ")) =>
                List("full")

            case cmd :: s :: Nil =>
                subCommandCompleter.complete(token, line)

            case _ =>
                Nil
        }
    }

    private def describeDatabase =
    {
        val metadata = connection.getMetaData
        val productName = metadata.getDatabaseProductName
        val productVersion = metadata.getDatabaseProductVersion
        val driverName = metadata.getDriverName
        val driverVersion = metadata.getDriverVersion

        val isolation =
            connection.getTransactionIsolation match
            {
                case Connection.TRANSACTION_READ_UNCOMMITTED =>
                    "read uncommitted"
                case Connection.TRANSACTION_READ_COMMITTED =>
                    "read committed"
                case Connection.TRANSACTION_REPEATABLE_READ =>
                    "repeatable read"
                case Connection.TRANSACTION_SERIALIZABLE =>
                    "serializable"
                case Connection.TRANSACTION_NONE =>
                    "none"
                case n =>
                    "unknown transaction isolation value of " + n.toString
            }

        val user = metadata.getUserName
        val displayUser = if ((user == null) || (user.trim == "")) null
                          else user

        val inTransactionStr = if (transactionManager.inTransaction) "yes"
                               else "no"

        // Need to use our own wrapper, to get a prefix.
        val w = new WordWrapper(79, 0, "                       ", ' ')
        println(w.wrap("Connected to database: " + metadata.getURL))
        if (user != null)
            println(w.wrap("Connected as user:     " + displayUser))
        println(w.wrap("Database vendor:       " + productName))
        println(w.wrap("Database version:      " + productVersion))
        println(w.wrap("JDBC driver:           " + driverName))
        println(w.wrap("JDBC driver version:   " + driverVersion))
        println(w.wrap("Transaction isolation: " + isolation))
        println(w.wrap("Open transaction?      " + inTransactionStr))
    }

    private def describeTable(table: String, full: Boolean) =
    {
        def getColumnDescriptions(md: ResultSetMetaData,
                                  i: Int): List[(String, String)] =
        {
            def precisionAndScale =
            {
                import scala.collection.mutable.ArrayBuffer

                val precision = md.getPrecision(i)
                val scale = md.getScale(i)
                val buf = new ArrayBuffer[String]

                if (precision > 0)
                    buf += precision.toString

                if (scale > 0)
                    buf += scale.toString

                buf.length match
                {
                    case 0 => ""
                    case _ => "(" + buf.mkString(", ") + ")"
                }
            }

            def charSize =
            {
                val size = md.getColumnDisplaySize(i)
                if (size > 0) "(" + size.toString + ")" else ""
            }

            if (i > md.getColumnCount)
                Nil

            else
            {
                val name = md.getColumnLabel(i) match
                {
                    case null => md.getColumnName(i)
                    case s    => s
                }

                val _typeName = md.getColumnTypeName(i)
                val jdbcType = md.getColumnType(i)
                // This weirdness handles SQLite, among other things.
                val typeName =
                    if ((_typeName != null) && (_typeName != "null"))
                        _typeName
                    else
                    {
                        if (jdbcType == SQLTypes.NULL) "?unknown?"
                        else jdbcTypeNames.getOrElse(jdbcType, "?unknown?")
                    }

                val typeQualifier = jdbcType match
                {
                    case SQLTypes.CHAR          => charSize
                    case SQLTypes.CLOB          => charSize
                    case SQLTypes.DECIMAL       => precisionAndScale
                    case SQLTypes.DOUBLE        => precisionAndScale
                    case SQLTypes.FLOAT         => precisionAndScale
                    case SQLTypes.LONGVARCHAR   => charSize
                    case SQLTypes.NUMERIC       => precisionAndScale
                    case SQLTypes.REAL          => precisionAndScale
                    case SQLTypes.VARCHAR       => charSize
                    case _                   => ""
                }

                val fullTypeName = typeName + typeQualifier
                val nullable = md.isNullable(i) match
                {
                    case ResultSetMetaData.columnNoNulls         => "NOT NULL"
                    case ResultSetMetaData.columnNullable        => "NULL"
                    case ResultSetMetaData.columnNullableUnknown => "NULL?"
                }

                (name, fullTypeName + " " + nullable) ::
                getColumnDescriptions(md, i + 1)
            }
        }

        withSQLStatement(connection)
        {
            statement =>

            val rs = statement.executeQuery("SELECT * FROM " +
                                            table + " WHERE 1 = 0")
            withResultSet(rs)
            {
                val metadata = rs.getMetaData
                val descriptions = getColumnDescriptions(metadata, 1)
                if (descriptions == Nil)
                    error("Can't get metadata for table \"" + table + "\"")
                else
                {
                    val width = max(descriptions.map(_._1.length): _*)
                    val fmt = "%-" + width + "s  %s"
                    // Note: Scala's format method doesn't left-justify.
                    def formatter = new java.util.Formatter

                    val colLines =
                        {for ((colName, colTypeName) <- descriptions)
                             yield(formatter.format(fmt, colName, colTypeName))}

                    // Use the table name returned from the driver.
                    val header = "Table " + table
                    val hr = "-" * header.length
                    println(List(hr, header, hr) mkString "\n")
                    println(colLines mkString ",\n")

                    if (full)
                    {
                        val schema = metadata.getSchemaName(1)
                        // Map the table name to what the database engine
                        // thinks the table's name should be. (Necessary
                        // for Oracle.)
                        findTableName(schema, table) match
                        {
                            case None =>
                            case Some(s) =>
                                showExtraTableData(metadata.getCatalogName(1),
                                                   schema,
                                                   s)
                        }
                    }
                }
            }
        }

        KeepGoing
    }

    private def showExtraTableData(catalog: String,
                                   schema: String,
                                   table: String) =
    {
        val dmd = connection.getMetaData

        showPrimaryKeys(dmd, catalog, schema, table)
        showIndexes(dmd, catalog, schema, table)
        showConstraints(dmd, catalog, schema, table)
    }

    private def findTableName(schema: String,
                              table: String): Option[String] =
    {
        val lcTable = table.toLowerCase
        val tables = shell.getTables(schema)
        val matching = tables.filter(ts =>
                                     (ts.name != None) &&
                                     (ts.name.get.toLowerCase == lcTable))

        matching.map(_.name.get) match
        {
            case tableName :: Nil =>
                Some(tableName)

            case tableName :: more =>
                error("Too many tables match \"" + table + "\": ")
                error(matching mkString ", ")
                None

            case Nil =>
                error("No tables match \"" + table + "\"")
                None
        }
    }

    private def showPrimaryKeys(dmd: DatabaseMetaData,
                                catalog: String,
                                schema: String,
                                table: String) =
    {
        def getPrimaryKeyColumns(rs: ResultSet): List[String]  =
        {
            if (! rs.next)
                Nil
            else
                rs.getString("COLUMN_NAME") :: getPrimaryKeyColumns(rs)
        }

        val rs = dmd.getPrimaryKeys(catalog, schema, table)
        withResultSet(rs)
        {
            val columns = getPrimaryKeyColumns(rs)
            if (columns != Nil)
                println("\nPrimary key columns: " + columns.mkString(", "))
        }
    }

    private def showIndexes(dmd: DatabaseMetaData,
                            catalog: String,
                            schema: String,
                            table: String) =
    {
        class IndexColumn(val columnName: String,
                          val unique: Boolean,
                          val ascending: Option[Boolean],
                          val indexType: String)

        import scala.collection.mutable.ArrayBuffer
        val uniqueIndexes = MutableMap.empty[String, ArrayBuffer[IndexColumn]]
        val nonUniqueIndexes = MutableMap.empty[String,
                                                ArrayBuffer[IndexColumn]]

        def gatherIndexInfo(rs: ResultSet): Unit =
        {
            if (rs.next)
            {
                val indexName = rs.getString("INDEX_NAME")
                val indexType = rs.getShort("TYPE") match
                {
                    case DatabaseMetaData.tableIndexClustered =>
                        "clustered"
                    case DatabaseMetaData.tableIndexHashed =>
                        "hashed"
                    case DatabaseMetaData.tableIndexOther =>
                        ""
                    case _ if (indexName != null) =>
                        ""
                    case _ =>
                        null
                }

                if (indexType != null)
                {
                    val ascending = rs.getString("ASC_OR_DESC") match
                    {
                        case "A" => Some(true)
                        case "D" => Some(false)
                        case _   => None
                    }

                    val unique = ! rs.getBoolean("NON_UNIQUE")
                    val column = rs.getString("COLUMN_NAME")

                    val indexInfo = if (unique) uniqueIndexes
                                    else nonUniqueIndexes
                    if (! (indexInfo contains indexName))
                        indexInfo += (indexName -> new ArrayBuffer[IndexColumn])
                    indexInfo(indexName) += new IndexColumn(column,
                                                            unique,
                                                            ascending,
                                                            indexType)
                }

                gatherIndexInfo(rs)
            }
        }

        def printIndex(indexName: String, columns: List[IndexColumn])
        {
            val buf = new StringBuilder
            val indexType =
                if (columns(0).indexType == null)
                    null
                else
                    columns(0).indexType.trim

            buf.append(indexName.trim)
            buf.append(": ")
            if (columns(0).unique)
                buf.append("Unique ")
            else
                buf.append("Non-unique ")

            if ((indexType != null) && (indexType != ""))
                buf.append(indexType + " index ")
            else
                buf.append("index ")

            buf.append("on (")
            buf.append(columns.map(_.columnName) mkString ", ")
            buf.append(")")
            wrapPrintln(buf.toString)
        }

        def nullIfEmpty(s: String) =
            if ((s == null) || (s.trim == "")) null else s

        val rs = dmd.getIndexInfo(nullIfEmpty(catalog),
                                  nullIfEmpty(schema),
                                  table,
                                  false,
                                  true)
        withResultSet(rs)
        {
            gatherIndexInfo(rs)
        }

        if ((uniqueIndexes.size + nonUniqueIndexes.size) > 0)
        {
            println()
            for (indexName <- uniqueIndexes.keys.toList.sort(nameSorter))
                printIndex(indexName, uniqueIndexes(indexName).toList)
            for (indexName <- nonUniqueIndexes.keys.toList.sort(nameSorter))
                printIndex(indexName, nonUniqueIndexes(indexName).toList)
        }
    }

    private def showConstraints(dmd: DatabaseMetaData,
                                catalog: String,
                                schema: String,
                                table: String) =
    {
        def checkForNull(s: String): String = if (s == null) "?" else s

        def printOne(rs: ResultSet): Unit =
        {
            if (rs.next)
            {
                val fk_name = checkForNull(rs.getString("FK_NAME"))
                printf("Foreign key %s: %s references %s.%s\n",
                       fk_name,
                       rs.getString("FKCOLUMN_NAME"),
                       rs.getString("PKTABLE_NAME"),
                       rs.getString("PKCOLUMN_NAME"));

                printOne(rs)
            }
        }

        val rs = dmd.getImportedKeys(catalog, schema, table)
        withResultSet(rs)
        {
            printOne(rs)
        }
    }

    private def getJdbcTypeNames: Map[Int, String] =
    {
        import java.lang.reflect.Modifier

        // Get the list of static int fields

        val staticIntFields = classOf[SQLTypes].getFields.filter(
            f => ((f.getModifiers & Modifier.STATIC) != 0) &&
                  (f.getType == classOf[Int]))

        // Create a map of int to name for those fields
        Map.empty[Int, String] ++
        staticIntFields.map(f => (f.getInt(null), f.getName))
    }
}

class ShowHandler(val shell: SQLShell, val connection: Connection)
    extends SQLShellCommandHandler with Wrapper with Sorter
{
    val CommandName = ".show"
    val Help =
"""|Show various useful things.
   |
   |.show tables/<schema> [pattern]
   |.show tables [pattern]
   |
   |    Show table names. "pattern" is a regular expression that can be used
   |    to filter the table names. In the first form of the command, the schema
   |    name is supplied, and tables from that schema are listed. In the second
   |    form of the command, the schema is taken from the default schema.
   |
   |    See ".set schema" for more information.
   |
   | .show schemas
   |    Show the names of all schemas in the database.""".stripMargin

    private val subCommands = List("tables", "schemas")
    private val subCommandCompleter = new ListCompleter(subCommands)
    private val ShowTables = """^\s*tables(/[^/.\s]+)?\s*$""".r
    private val ShowSchemas = """^\s*(schemas)\s*$""".r

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        args.tokenize match
        {
            case ShowTables(schema) :: Nil =>
                showTables(schema, ".*")
            case ShowTables(schema) :: pattern :: Nil =>
                showTables(schema, pattern)
            case ShowSchemas(s) :: Nil =>
                showSchemas
            case Nil =>
                shell.error("Missing the things to show.")
            case _ =>
                shell.error("Bad .show command: \"" + args + "\"")
        }

        KeepGoing
    }

    override def complete(token: String, line: String): List[String] =
    {
        line.tokenize match
        {
            case Nil =>
                assert(false) // shouldn't happen
                Nil

            case cmd :: Nil =>
                // Command filled in (obviously, or we wouldn't be in here),
                // but first argument not.
                subCommandCompleter.complete(token, line)

            case cmd :: ShowTables(s) :: Nil =>
                Nil

            case cmd :: ShowSchemas(s) :: Nil =>
                Nil

            case cmd :: s :: Nil =>
                subCommandCompleter.complete(token, line)

            case _ =>
                Nil
        }
    }

    private def showTables(schema: String, pattern: String) =
    {
        val nameFilter = new Regex("(?i)" + pattern) // case insensitive

        val schemaOption = schema match
        {
            case null => None
            case _    => if (schema.startsWith("/"))
                             Some(schema.substring(1))
                         else
                             Some(schema)
        }

        val tables: List[TableSpec] = shell.getTables(schemaOption, nameFilter)
        val tableNames = tables.filter(_.name != None).map(_.name.get)
        val sorted = tableNames sort (nameSorter)
        print(shell.columnarize(sorted, shell.OutputWidth))

        KeepGoing
    }

    private def showSchemas =
    {
        val schemas = shell.getSchemas
        val sorted = schemas.sort((a, b) => a.toLowerCase < b.toLowerCase)
        print(shell.columnarize(sorted, shell.OutputWidth))
    }
}
