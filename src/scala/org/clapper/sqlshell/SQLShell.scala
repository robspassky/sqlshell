package org.clapper.sqlshell

import grizzled.cmd._
import grizzled.readline.ListCompleter
import grizzled.readline.Readline
import grizzled.readline.Readline.ReadlineType
import grizzled.readline.Readline.ReadlineType._
import grizzled.GrizzledString._
import grizzled.string.WordWrapper
import grizzled.config.Configuration

import java.sql.{Connection,
                 ResultSet,
                 ResultSetMetaData,
                 Statement,
                 SQLException,
                 Types}
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat

import scala.collection.mutable.{Map => MutableMap}
import scala.util.matching.Regex

object Ident
{
    val Version = "0.1"
    val Name = "sqlshell"
    val Copyright = "Copyright (c) 2009 Brian M. Clapper. All rights reserved."

    val IdentString = "%s, version %s\n%s" format (Name, Version, Copyright)
}

abstract sealed class SettingType
case object IntSetting extends SettingType
case object StringSetting extends SettingType
case object BooleanSetting extends SettingType

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

class SQLShell(val config: Configuration,
               dbInfo: DatabaseInfo,
               readlineLibs: List[ReadlineType],
               showStackTraces: Boolean)
    extends CommandInterpreter("sqlshell", readlineLibs) with Wrapper
{
    private[sqlshell] val settings = MutableMap[String, (SettingType, Any)](
        "autocommit"    -> (BooleanSetting, true),
        "schema"        -> (StringSetting, None),
        "showbinary"    -> (IntSetting, 0),
        "showrowcount"  -> (BooleanSetting, true),
        "showtimings"   -> (BooleanSetting, true),
        "stacktrace"    -> (BooleanSetting, showStackTraces)
    )

    println(Ident.IdentString)
    println("Using " + readline + " readline implementation.")

    loadSettings(config)

    val connector = new DatabaseConnector(config)
    val connectionInfo = connector.connect(dbInfo)
    val connection = connectionInfo.connection
    val historyPath = connectionInfo.configInfo.get("history")
    if (connectionInfo.configInfo.get("schema") != None)
        changeSetting("schema", connectionInfo.configInfo.get("schema").get)

    val handlers = List(new HistoryHandler(this),
                        new RedoHandler(this),
                        new SelectHandler(this, connection),
                        new InsertHandler(this, connection),
                        new DeleteHandler(this, connection),
                        new UpdateHandler(this, connection),
                        new ShowHandler(this, connection),
                        new SetHandler(this))
    private val unknownHandler = new UnknownHandler(this, connection)

    // Allow "." characters in commands.
    override def StartCommandIdentifier = super.StartCommandIdentifier + "."

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
        if (booleanSettingIsTrue("stacktrace"))
            e.printStackTrace(System.out)

        KeepGoing
    }

    override def preCommand(line: String) =
        if (line.ltrim.startsWith("--"))
            ""
        else
            line

    def booleanSettingIsTrue(variableName: String): Boolean =
    {
        assert(settings contains variableName)

        val (valueType, value) = settings(variableName)
        assert(valueType == BooleanSetting)
        value.asInstanceOf[Boolean]
    }

    def intSetting(variableName: String): Int =
    {
        assert(settings contains variableName)

        val (valueType, value) = settings(variableName)
        assert(valueType == IntSetting)
        value.asInstanceOf[Int]
    }

    def stringSetting(variableName: String): Option[String] =
    {
        assert(settings contains variableName)

        val (valueType, value) = settings(variableName)
        assert(valueType == StringSetting)
        val sValue = value.asInstanceOf[String]
        Some(sValue)
    }

    def printSettingValue(variableName: String) =
    {
        settings.get(variableName) match
        {
            case None =>
                warning("Unknown setting: \"" + variableName + "\"")

            case Some(tuple) =>
                println(variableName + "=" + tuple._2)
        }
    }

    def changeSetting(variable: String, value: String) =
    {
        settings.get(variable) match
        {
            case None =>
                warning("Unknown setting: \"" + variable + "\"")

            case Some(tuple) =>
                convertAndStoreSetting(variable, value, tuple._1)
        }
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

        val actualSchema = schema match
        {
            case None =>
                stringSetting("schema")
            case Some(s) =>
                Some(s)
        }

        actualSchema match
        {
            case None =>
                wrapPrintln("No schema specified, and no default schema set. " +
                            "To set a default schema, use: " +
                            ".set schema schemaName\n" +
                            "Use a schema name of * for all schemas.")
                Nil

            case Some(schema) =>
                val metadata = connection.getMetaData
                val rs = metadata.getTables(null, schema, null, null)
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
            println("Loading \"settings\" section from the configuration.")

            for ((variable, value) <- config.options("settings"))
                changeSetting(variable, value)
        }

        
    }

    private def convertAndStoreSetting(variable: String, 
                                       value: String, 
                                       valueType: SettingType) =
    {
        import grizzled.string.implicits._

        valueType match
        {
            case IntSetting =>
                try
                {
                    settings(variable) = (valueType, value.toInt)
                }

                catch
                {
                    case e: NumberFormatException =>
                        error("Attempt to set \"" + variable + "\" to \"" +
                              value + "\" failed: Bad numeric value.")
                }

            case BooleanSetting =>
                try
                {
                    val boolValue: Boolean = value
                    settings(variable) = (valueType, boolValue)
                }

                catch
                {
                    case e: IllegalArgumentException =>
                        error("Attempt to set \"" + variable + "\" to \"" +
                              value + "\" failed: " + e.getMessage)
                }

            case StringSetting =>
                settings(variable) = (valueType, value)

            case _ =>
                assert(false)
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
 * Handles the ".set" command.
 */
class SetHandler(val shell: SQLShell) extends CommandHandler
{
    val CommandName = ".set"
    val Help = """Change one of the SQLShell settings. Usage:
                 |
                 |    .set            -- show the current settings
                 |    .set var=value  -- change a variable""".stripMargin

    val variables = shell.settings.keys.toList.sort((a, b) => a < b)
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
            if (chopAt < 0)
            {
                val variable = args.trim
                shell.printSettingValue(variable)
            }

            else
            {
                val variable = args.substring(0, chopAt).trim
                val value = args.substring(chopAt + 1).trim
                shell.changeSetting(variable, value)
            }
        }

        KeepGoing
    }

    override def complete(token: String, line: String): List[String] =
        completer.complete(token, line)

    private def showSettings =
    {
        import grizzled.math.util._

        val varWidth = max(variables.map(_.length): _*)
        val fmt = "%" + varWidth + "s: %s"

        for (variable <- variables)
        {
            var (valueType, value) = shell.settings(variable)
            println(fmt format(variable, value))
        }
    }
}

private[sqlshell] abstract class SQLHandler(val shell: SQLShell,
                                            val connection: Connection) 
    extends CommandHandler with Timer
{
    override def moreInputNeeded(lineSoFar: String): Boolean =
        (! lineSoFar.ltrim.endsWith(";"))

   protected def withSQLStatement(code: (Statement) => Unit) =
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

    protected def removeSemicolon(s: String): String =
        if (s endsWith ";")
            s.rtrim.substring(0, s.length - 1)
        else
            s
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
                    ObjectInputStream,
                    ObjectOutputStream}

    val DateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S")
    val CommandName = "select"
    val Help = """Issue a SQL SELECT statement and display the results"""
    val ColumnSeparator = "  "

    val tempFile = File.createTempFile("sqlshell", ".dat")
    tempFile.deleteOnExit

    def runCommand(commandName: String, args: String): CommandAction =
    {
        val newArgs = removeSemicolon(args)
        withSQLStatement
        {
            statement =>

            val (elapsed, rs) = 
                time
                {
                    statement.executeQuery(commandName + " " + newArgs)
                }
            try
            {
                dumpResults(elapsed, rs)
            }

            finally
            {
                rs.close
            }
        }

        KeepGoing
    }

    private def dumpResults(elapsed: Long, rs: ResultSet) =
    {
        val (rows, colNamesAndSizes, dataFile) = preprocess(rs)

        if (shell.booleanSettingIsTrue("showrowcount"))
        {
            if (rows == 0)
                println("No rows returned.")
            else if (rows == 1)
                println("1 row returned.")
            else
                println(rows + " rows returned.")
        }

        if (shell.booleanSettingIsTrue("showtimings"))
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
        def getDateString(date: Date): String = DateFormatter.format(date)

        def nonNullColAsString(i: Int): String =
        {
            metadata.getColumnType(i) match
            {
                case Types.ARRAY         => rs.getArray(i).toString
                case Types.BIGINT        => rs.getLong(i).toString
                case Types.BINARY        => "<binary>"
                case Types.BLOB          => "<binary>"
                case Types.BOOLEAN       => rs.getBoolean(i).toString
                case Types.CHAR          => rs.getString(9)
                case Types.CLOB          => "<clob>"
                case Types.DATE          => getDateString(rs.getDate(i))
                case Types.DECIMAL       => rs.getBigDecimal(i).toString
                case Types.DOUBLE        => rs.getDouble(i).toString
                case Types.FLOAT         => rs.getFloat(i).toString
                case Types.INTEGER       => rs.getInt(i).toString
                case Types.LONGVARBINARY => "<longvarbinary>"
                case Types.LONGVARCHAR   => "<longvarchar>"
                case Types.NULL          => "<null>"
                case Types.NUMERIC       => rs.getDouble(i).toString
                case Types.REAL          => rs.getDouble(i).toString
                case Types.SMALLINT      => rs.getInt(i).toString
                case Types.TIME          => getDateString(rs.getTime(i))
                case Types.TIMESTAMP     => getDateString(rs.getTimestamp(i))
                case Types.TINYINT       => rs.getInt(i).toString
                case Types.VARBINARY     => "<varbinary>"
                case Types.VARCHAR       => rs.getString(i).toString
                case _                   => rs.getObject(i).toString
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
        withSQLStatement
        {
            statement =>

            val (elapsed, rows) =
                time
                {
                    statement.executeUpdate(commandName + " " + newArgs)
                }

            if (shell.booleanSettingIsTrue("showrowcount"))
            {
                if (rows == 0)
                    println("No rows affected.")
                else if (rows == 1)
                    println("1 row affected.")
                else
                    println(rows + " rows affected.")
            }

            if (shell.booleanSettingIsTrue("showtimings"))
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

private[sqlshell] class UnknownHandler(shell: SQLShell, connection: Connection)
    extends AnyUpdateHandler(shell, connection)
{
    val CommandName = ""
    val Help = """Issue an unknown SQL statement."""
}

private[sqlshell] class ShowHandler(val shell: SQLShell, 
                                    val connection: Connection)
    extends CommandHandler with TableNameRetriever with Wrapper
{
    val CommandName = ".show"
    val Help = """Show various useful things.
    |
    |.show database
    |    Show information about the database.
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

    private val subCommands = List("tables", "database", "schemas")
    private val subCommandCompleter = new ListCompleter(subCommands)
    private val ShowTables = """^\s*tables(/[^/.\s]+)?\s*$""".r
    private val ShowDatabase = """^\s*(database)\s*$""".r
    private val ShowSchemas = """^\s*(schemas)\s*$""".r

    def runCommand(commandName: String, args: String): CommandAction =
    {
        args.trim.split("""\s""").toList match
        {
            case ShowTables(schema) :: Nil =>
                showTables(schema, ".*")
            case ShowTables(schema) :: pattern :: Nil => 
                showTables(schema, pattern)
            case ShowDatabase(s) :: Nil =>
                showDatabase
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

            case cmd :: ShowDatabase(s) :: Nil =>
                Nil

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

    private def showDatabase =
    {
        val metadata = connection.getMetaData
        val productName = metadata.getDatabaseProductName
        val productVersion = metadata.getDatabaseProductVersion
        val driverName = metadata.getDriverName
        val driverVersion = metadata.getDriverVersion

        println(productName + ", " + productVersion)
        wrapPrintln("Using JDBC driver " + driverName + ", " + driverVersion)
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
        val sorted = tableNames sort ((a, b) => a.toLowerCase < b.toLowerCase)
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
