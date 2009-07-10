package org.clapper.sqlshell

import grizzled.cmd._
import grizzled.readline.ListCompleter
import grizzled.GrizzledString._
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

class SQLShell(val config: Configuration,
               dbInfo: DatabaseInfo,
               showStackTraces: Boolean)
    extends CommandInterpreter("sqlshell")
{
    private[sqlshell] val settings = MutableMap[String, (SettingType, Any)](
        "autocommit"    -> (BooleanSetting, true),
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
    val handlers = List(new HistoryHandler(this),
                        new RedoHandler(this),
                        new SelectHandler(this, connection),
                        new SetHandler(this))

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
            args.trim.split("[=]").toList match
            {
                case variable :: value :: Nil =>
                    shell.changeSetting(variable, value)

                case _ =>
                    shell.error("Usage: .set [var=value]")
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

/**
 * Handles SQL "SELECT" statements.
 */
class SelectHandler(val shell: SQLShell,
                    val connection: Connection) 
    extends CommandHandler with Timer
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
        // Remove the trailing semicolon.
        val newArgs = args.rtrim.substring(0, args.length - 1)
        val statement = connection.createStatement
        val (elapsed, rs) = 
            time
            {
                statement.executeQuery(commandName + " " + newArgs)
            }
        dumpResults(elapsed, rs)
        KeepGoing
    }

    override def moreInputNeeded(lineSoFar: String): Boolean =
        (! lineSoFar.ltrim.endsWith(";"))

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
 * Base handler for non-query SQL that's to be executed (e.g., UPDATE, INSERT,
 * etc.).
 */
abstract class ExecuteSQLHandler(val shell: SQLShell,
                                 val connection: Connection) 
    extends CommandHandler
{
    def runCommand(commandName: String, args: String): CommandAction =
    {
        KeepGoing
    }
}
