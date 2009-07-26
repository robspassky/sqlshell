package org.clapper.sqlshell

import grizzled.cmd._
import grizzled.readline.ListCompleter
import grizzled.readline.Readline
import grizzled.readline.Readline.ReadlineType
import grizzled.readline.Readline.ReadlineType._
import grizzled.GrizzledString._
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

private[sqlshell] class TableSpec(val name: Option[String],
                                  val schema: Option[String],
                                  val tableType: Option[String])

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
 */
class SQLShell(val config: Configuration,
               dbInfo: DatabaseInfo,
               readlineLibs: List[ReadlineType],
               useAnsiColors: Boolean,
               showStackTraces: Boolean)
    extends CommandInterpreter("sqlshell", readlineLibs) 
    with Wrapper with Sorter
{
    private[sqlshell] val settings = new Settings(
        ("autocommit",   BooleanSetting, true),
        ("ansi",         BooleanSetting, useAnsiColors),
        ("echo",         BooleanSetting, false),
        ("schema",       StringSetting, ""),
        ("showbinary",   IntSetting, 0),
        ("showrowcount", BooleanSetting, true),
        ("showtimings",  BooleanSetting, true),
        ("stacktrace",   BooleanSetting, showStackTraces)
    )

    loadSettings(config)

    val connector = new DatabaseConnector(config)
    val connectionInfo = connector.connect(dbInfo)
    val connection = connectionInfo.connection
    val historyPath = connectionInfo.configInfo.get("history")
    if (connectionInfo.configInfo.get("schema") != None)
        settings.changeSetting("schema", 
                               connectionInfo.configInfo.get("schema").get)

    // List of command handlers.

    val aboutHandler = new AboutHandler(this)
    val handlers = List(new HistoryHandler(this),
                        new RedoHandler(this),
                        new SelectHandler(this, connection),
                        new InsertHandler(this, connection),
                        new DeleteHandler(this, connection),
                        new UpdateHandler(this, connection),
                        new CreateHandler(this, connection),
                        new AlterHandler(this, connection),
                        new DropHandler(this, connection),
                        new ShowHandler(this, connection),
                        new DescribeHandler(this, connection),
                        new SetHandler(this),
                        new ExitHandler,
                        aboutHandler)

    aboutHandler.show
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

    override def preLoop: Unit =
    {
        historyPath match
        {
            case None =>
                return

            case Some(path) =>
                println("Loading history from \"" + path + "\"...")
                history.load(path)
        }
    }

    override def postLoop: Unit =
    {
        historyPath match
        {
            case None =>
                return

            case Some(path) =>
                println("Saving history to \"" + path + "\"...")
                history.save(path)
        }
    }

    override def handleEOF: CommandAction =
    {
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
            Some(line)
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

    private def loadSettings(config: Configuration) =
    {
        if (config.hasSection("settings"))
        {
            for ((variable, value) <- config.options("settings"))
                try
                {
                    settings.changeSetting(variable, value)
                }
                catch
                {
                    case e: UnknownVariableException => warning(e.message)
                }
        }
    }
}

/**
 * Timer mix-in.
 */
private[sqlshell] trait Timer
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

/**
 * Handles the "exit" command
 */
class ExitHandler extends CommandHandler
{
    val CommandName = "exit"
    val Help = "Exit SQLShell."

    def runCommand(commandName: String, args: String): CommandAction = Stop
}

/**
 * Handles the ".set" command.
 */
class SetHandler(val shell: SQLShell) extends CommandHandler with Sorter
{
    val CommandName = ".set"
    val Help = """Change one of the SQLShell settings. Usage:
                 |
                 |    .set            -- show the current settings
                 |    .set var value  -- change a variable
                 |    .set var=value  -- change a variable""".stripMargin

    val variables = shell.settings.variableNames
    val completer = new ListCompleter(variables)

    def runCommand(commandName: String, args: String): CommandAction =
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
                    println(shell.settings.settingValueToString(variable))
                }

                else
                {
                    val variable = args.substring(0, chopAt).trim
                    val value = args.substring(chopAt + 1).trim
                    shell.settings.changeSetting(variable, value)
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

    private def showSettings =
    {
        val varWidth = max(variables.map(_.length): _*)
        val fmt = "%" + varWidth + "s: %s"

        for (variable <- variables)
        {
            var (valueType, value) = shell.settings(variable)
            println(fmt format(variable, value))
        }
    }
}

/**
 * Handles the ".about" command.
 */
class AboutHandler(val shell: SQLShell) extends CommandHandler with Sorter
{
    val CommandName = ".about"
    val Help = "Display information about SQLShell"

    def runCommand(commandName: String, args: String): CommandAction =
    {
        show
        KeepGoing
    }

    private[sqlshell] def show =
    {
        println(Ident.IdentString)
        println()
        println("Using " + shell.readline + " readline implementation.")
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

private[sqlshell] abstract class SQLHandler(val shell: SQLShell,
                                            val connection: Connection) 
    extends CommandHandler with Timer with JDBCHelper
{
    override def moreInputNeeded(lineSoFar: String): Boolean =
        (! lineSoFar.ltrim.endsWith(";"))

    protected def removeSemicolon(s: String): String =
        if (s endsWith ";")
            s.rtrim.substring(0, s.length - 1)
        else
            s

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
private[sqlshell] class SelectHandler(shell: SQLShell,
                                      connection: Connection) 
    extends SQLHandler(shell, connection) with Timer
{
    import java.io.{EOFException,
                    FileInputStream,
                    FileOutputStream,
                    InputStream,
                    ObjectInputStream,
                    ObjectOutputStream,
                    Reader}

    val DateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S")
    val CommandName = "select"
    val Help = """Issue a SQL SELECT statement and display the results"""
    val ColumnSeparator = "  "

    val tempFile = File.createTempFile("sqlshell", ".dat")
    tempFile.deleteOnExit

    def runCommand(commandName: String, args: String): CommandAction =
    {
        val newArgs = removeSemicolon(args)
        withSQLStatement(connection)
        {
            statement =>

            val (elapsed, rs) = 
                time
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

    protected def dumpResults(elapsed: Long, rs: ResultSet) =
    {
        val (rows, colNamesAndSizes, dataFile) = preprocess(rs)

        if (shell.settings.booleanSettingIsTrue("showrowcount"))
        {
            if (rows == 0)
                println("No rows returned.")
            else if (rows == 1)
                println("1 row returned.")
            else
                println(rows + " rows returned.")
        }

        if (shell.settings.booleanSettingIsTrue("showtimings"))
            println("Execution time: " + formatInterval(elapsed))

        // Note: Scala's format method doesn't left-justify.
        def formatter = new java.util.Formatter

        // Print column names...
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
        val rowsIn = new ObjectInputStream(new FileInputStream(dataFile))

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

    private def preprocess(rs: ResultSet) =
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

            val rows = preprocessResultRow(rs, 0)
            (rows, colNamesAndSizes, tempFile)
        }

        finally
        {
            tempOut.close
        }
    }

    private def mapRow(rs: ResultSet,
                       metadata: ResultSetMetaData): Map[String, String] =
    {
        import grizzled.io.implicits._

        def getDateString(date: Date): String = DateFormatter.format(date)

        def clobString(r: Reader): String =
        {
            try
            {
                val binaryLength = shell.settings.intSetting("showbinary")

                if (binaryLength == 0)
                    "<clob>"

                else
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
            }

            finally
            {
                r.close
            }
        }

        def binaryString(is: InputStream): String =
        {
            try
            {
                val binaryLength = shell.settings.intSetting("showbinary")
                if (binaryLength == 0)
                    "<binary>"

                else
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
            }

            finally
            {
                is.close
            }
        }

        def nonNullColAsString(i: Int): String =
        {
            metadata.getColumnType(i) match
            {
                case SQLTypes.ARRAY         => rs.getArray(i).toString
                case SQLTypes.BIGINT        => rs.getLong(i).toString
                case SQLTypes.BINARY        => binaryString(rs.getBinaryStream(i))
                case SQLTypes.BLOB          => binaryString(rs.getBinaryStream(i))
                case SQLTypes.BOOLEAN       => rs.getBoolean(i).toString
                case SQLTypes.CHAR          => rs.getString(i)
                case SQLTypes.CLOB          => clobString(rs.getCharacterStream(i))
                case SQLTypes.DATE          => getDateString(rs.getDate(i))
                case SQLTypes.DECIMAL       => rs.getBigDecimal(i).toString
                case SQLTypes.DOUBLE        => rs.getDouble(i).toString
                case SQLTypes.FLOAT         => rs.getFloat(i).toString
                case SQLTypes.INTEGER       => rs.getInt(i).toString
                case SQLTypes.LONGVARBINARY => binaryString(rs.getBinaryStream(i))
                case SQLTypes.LONGVARCHAR   => clobString(rs.getCharacterStream(i))
                case SQLTypes.NULL          => "<null>"
                case SQLTypes.NUMERIC       => rs.getDouble(i).toString
                case SQLTypes.REAL          => rs.getDouble(i).toString
                case SQLTypes.SMALLINT      => rs.getInt(i).toString
                case SQLTypes.TIME          => getDateString(rs.getTime(i))
                case SQLTypes.TIMESTAMP     => getDateString(rs.getTimestamp(i))
                case SQLTypes.TINYINT       => rs.getInt(i).toString
                case SQLTypes.VARBINARY     => binaryString(rs.getBinaryStream(i))
                case SQLTypes.VARCHAR       => rs.getString(i).toString
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

    private def loadResultRow(os: ObjectInputStream): Map[String,String] =
    {
        Map.empty[String,String]
    }
}

/**
 * Handles any command that calls Statement.executeUpdate(). This class
 * is abstract so that specific handlers can be instantiated for individual
 * commands (allowing individual help).
 */
private[sqlshell] abstract class AnyUpdateHandler(shell: SQLShell,
                                                  connection: Connection) 
    extends SQLHandler(shell, connection) with Timer
{
    def runCommand(commandName: String, args: String): CommandAction =
    {
        val newArgs = removeSemicolon(args)
        withSQLStatement(connection)
        {
            statement =>

            val (elapsed, rows) =
                time
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
                    println(rows + " rows affected.")
            }

            if (shell.settings.booleanSettingIsTrue("showtimings"))
                println("Execution time: " + formatInterval(elapsed))
        }

        KeepGoing
    }
}

private[sqlshell] class UpdateHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "update"
    val Help = """Issue a SQL UPDATE statement."""
}

private[sqlshell] class InsertHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "insert"
    val Help = """Issue a SQL INSERT statement."""
}

private[sqlshell] class DeleteHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "delete"
    val Help = """Issue a SQL DELETE statement."""
}

private[sqlshell] class AlterHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "alter"
    val Help = """Issue a SQL ALTER statement."""
}

private[sqlshell] class CreateHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "create"
    val Help = """Issue a SQL CREATE statement."""
}

private[sqlshell] class DropHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = "drop"
    val Help = """Issue a SQL DROP statement."""
}

private[sqlshell] class UnknownHandler(shell: SQLShell, connection: Connection)
    extends CommandHandler
{
    val CommandName = ""
    val Help = """Issue an unknown SQL statement."""

    val updateHandler = new UpdateHandler(shell, connection)
    val queryHandler = new SelectHandler(shell, connection)

    override def runCommand(commandName: String, args: String): CommandAction =
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

private[sqlshell] class DescribeHandler(val shell: SQLShell, 
                                        val connection: Connection)
    extends CommandHandler 
    with Wrapper with JDBCHelper with Sorter
{
    val CommandName = ".desc"
    val Help = """Show various useful things.
    |
    |.desc database
    |    Show information about the database.
    |
    |.desc table [full]
    |
    |    Describe a table, showing the column names and their types. If "full"
    |    is specified, show the indexes and constraints, as well.
    |
    |    In the first form of the command, the schema name identifies the
    |    schema in which to find the table. In the second form of the
    |    command, the schema is taken from the default schema (see 
    |    ".set schema").""".stripMargin

    private val jdbcTypeNames = getJdbcTypeNames

    private val subCommands = List("database")
    private def subCommandCompleter =
    {
        val tables = shell.getTableNames(None)
        new ListCompleter(subCommands ++ tables, _.toLowerCase)
    }

    def runCommand(commandName: String, args: String): CommandAction =
    {
        args.trim.split("""\s""").toList match
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
        line.split("""\s""").toList match
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

        println(productName + ", " + productVersion)
        wrapPrintln("Using JDBC driver " + driverName + ", " + driverVersion)
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
            val buf = new ArrayBuffer[String]
            val indexType = columns(0).indexType
            if (columns(0).unique)
                buf += "Unique"
            else
                buf += "Non-unique"

            if ((indexType != null) && (indexType.trim != ""))
                buf += (indexType + " index ")
            else
                buf += "index"

            buf += indexName
            buf += "on:"
            buf += (columns.map(_.columnName) mkString ", ")
            wrapPrintln(buf mkString " ")
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

private[sqlshell] class ShowHandler(val shell: SQLShell, 
                                    val connection: Connection)
    extends CommandHandler with Wrapper with Sorter
{
    val CommandName = ".show"
    val Help = """Show various useful things.
    |
    |.show tables/<schema> [pattern]
    |.show tables [pattern]
    |
    |    Show table names. "pattern" is a regular expression that can be used
    |    to filter the table names. In the first form of the command, the
    |    schema name is supplied, and tables from that schema are listed. In
    |    the second form of the command, the schema is taken from the default
    |    schema (see ".set schema").
    |
    | .show schemas
    |    Show the names of all schemas in the database.""".stripMargin

    private val subCommands = List("tables", "schemas")
    private val subCommandCompleter = new ListCompleter(subCommands)
    private val ShowTables = """^\s*tables(/[^/.\s]+)?\s*$""".r
    private val ShowSchemas = """^\s*(schemas)\s*$""".r

    def runCommand(commandName: String, args: String): CommandAction =
    {
        args.trim.split("""\s""").toList match
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
        line.split("""\s""").toList match
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
