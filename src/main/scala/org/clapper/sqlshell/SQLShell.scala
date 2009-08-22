/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2009, Brian M. Clapper
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the names "clapper.org", "SQLShell", nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  ---------------------------------------------------------------------------
*/

package org.clapper.sqlshell

import org.clapper.sqlshell.log.{logger, Verbose}

import grizzled.cmd._
import grizzled.readline.{ListCompleter,
                          PathnameCompleter,
                          CompletionToken,
                          CompleterHelper,
                          LineToken,
                          Delim,
                          Cursor}
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

import scala.collection.mutable.{ArrayBuffer,
                                 Map => MutableMap,
                                 Set => MutableSet}
import scala.io.Source
import scala.util.matching.Regex

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
 * Handles enabling/disabling verbose messages.
 */
object LogLevelSetting extends Setting with ValueConverter
{
    override val legalValues = logger.Levels.map(_.toString)
    override def get = logger.level.toString
    override def set(newValue: Any) = logger.level = newValue
    override def convertString(newValue: String): Any = newValue
}

/**
 * Handles changes to the "ansi" setting.
 */
object AnsiSetting extends Setting with BooleanValueConverter
{
    override def get = logger.useAnsi

    override def set(newValue: Any) =
        logger.useAnsi = newValue.asInstanceOf[Boolean]
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

    if (beVerbose)
        logger.level = Verbose

    AnsiSetting.set(useAnsiColors)

    val settings = new Settings(
        ("ansi",         AnsiSetting),
        ("catalog",      new StringSetting("")),
        ("echo",         new BooleanSetting(false)),
        ("logging",      LogLevelSetting),
        ("maxhistory",   new MaxHistorySetting(readline)),
        ("schema",       new StringSetting("")),
        ("showbinary",   new IntSetting(0)),
        ("showrowcount", new BooleanSetting(true)),
        ("showtimings",  new BooleanSetting(true)),
        ("stacktrace",   new BooleanSetting(showStackTraces))
    )

    // List of command handlers.

    val aboutHandler = new AboutHandler(this)
    val setHandler = new SetHandler(this)
    val selectHandler = new SelectHandler(this, connection)
    val updateHandler = new UpdateHandler(this, connection)
    val transactionManager = new TransactionManager(this, connection)
    val handlers = transactionManager.handlers ++
                   List(new HistoryHandler(this),
                        new RedoHandler(this),
                        new RunFileHandler(this),

                        selectHandler,
                        new InsertHandler(this, connection),
                        new DeleteHandler(this, connection),
                        updateHandler,
                        new CreateHandler(this, connection),
                        new AlterHandler(this, connection),
                        new DropHandler(this, connection),

                        new CaptureHandler(this, selectHandler),
                        new ShowHandler(this, connection),
                        new DescribeHandler(this, connection,
                                            transactionManager),
                        setHandler,
                        new EchoHandler,
                        new ExitHandler,
                        aboutHandler)

    loadSettings(config, connectionInfo)
    aboutHandler.showAbbreviatedInfo
    logger.verbose("Connected to: " + connectionInfo.jdbcURL)
    println()

    if (fileToRun != None)
    {
        // Push a reader for the file on the stack. To ensure that we don't
        // fall through to interactive mode, make sure there's an "exit" at
        // the end.

        pushReader(new SourceReader(Source.fromString("exit\n")).readline)
        pushReader(new SourceReader(Source.fromFile(fileToRun.get)).readline)
    }

    private val unknownHandler = new UnknownHandler(this,
                                                    selectHandler,
                                                    updateHandler)

    // Allow "." characters in commands.
    override def StartCommandIdentifier = super.StartCommandIdentifier + "."

    /**
     * The primary prompt string.
     */
    override def primaryPrompt = "sqlshell> "

    /**
     * The second prompt string, used when additional input is being
     * retrieved.
     */
    override def secondaryPrompt = "> "

    override def preLoop: Unit =
    {
        historyPath match
        {
            case None =>
                return

            case Some(path) =>
                logger.verbose("Loading history from \"" + path + "\"...")
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
                logger.verbose("Saving history to \"" + path + "\"...")
                history.save(path)
        }
    }

    override def handleEOF: CommandAction =
    {
        logger.verbose("EOF. Exiting.")
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

        logger.verbose("Caught exception.")
        logger.error(message)
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

    override def postCommand(command: String,
                             unparsedArgs: String): CommandAction =
    {
        // Force a newline after each command's output.
        println()
        return KeepGoing
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

        // Note that it's possible for the retrieval of metadata to fail.
        // Some databases (e.g., MS Access over the JDBC-ODBC bridge) don't
        // support it.

        try
        {
            val jdbcSchema = 
                if (schema == None)
                    settings.stringSetting("schema", null)
                else
                    schema.get
            val catalog = settings.stringSetting("catalog", null)
            val metadata = connection.getMetaData
            logger.verbose("Getting list of tables. schema=" + jdbcSchema +
                           ", catalog=" + catalog)
            val rs = metadata.getTables(catalog, jdbcSchema, null,
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

        catch
        {
            case e: SQLException =>
                logger.verbose("Failed to retrieve metadata: " + e.getMessage)
                Nil
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
     * Get a list of table names that match a prefix. Useful for completers.
     *
     * @param schema  the schema to use, or None for the default
     * @param prefix  the table name prefix, or None to match all tables
     *
     * @return a sorted list of table names, or Nil for no match
     */
    private[sqlshell] def matchTableNames(schema: Option[String],
                                          prefix: Option[String]):
        List[String] =
    {
        prefix match
        {
            case None =>
                getTableNames(schema)

            case Some(p) =>
                val tables = getTables(schema)
                val lcPrefix = p.toLowerCase
                tables.filter(ts =>
                              (ts.name != None) &&
                              (ts.name.get.toLowerCase.startsWith(lcPrefix)))
                      .map(_.name.get)
        }
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
            logger.verbose("Loading settings from configuration.")
            for ((variable, value) <- config.options("settings"))
                try
                {
                    settings.changeSetting(variable, value)
                    logger.verbose("+ " + setHandler.CommandName + " " +
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
                logger.verbose("+ " + setHandler.CommandName + " schema=" + 
                               schema)
        }
    }
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
                logger.error("You must specify a file to be run.")
            case file :: Nil =>
                val reader = new SourceReader(Source.fromFile(file))
                logger.verbose("Loading and running \"" + file + "\"")
                shell.pushReader(reader.readline)
            case _ =>
                logger.error("Too many parameters to " + CommandName + 
                             " command.")
        }

        KeepGoing
    }

    override def complete(token: String,
                          allTokens: List[CompletionToken],
                          line: String): List[String] =
        completer.complete(token, allTokens, line)
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
    val varCompleter = new ListCompleter(variables)

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
                case e: UnknownVariableException => logger.warning(e.message)
            }
        }

        KeepGoing
    }

    override def complete(token: String,
                          allTokens: List[CompletionToken],
                          line: String): List[String] =
    {
        def varValues(varName: String): List[String] =
        {
            try
            {
                shell.settings.legalValuesFor(varName)
            }
            catch
            {
                case e: UnknownVariableException => Nil
            }
        }

        allTokens match
        {
            case Nil =>
                assert(false) // shouldn't happen
                Nil

            case LineToken(cmd) :: Cursor :: rest =>
                Nil

            case LineToken(cmd) :: Delim :: Cursor :: rest =>
                varCompleter.complete(token, allTokens, line)

            case LineToken(cmd) :: Delim :: LineToken(varName) ::
                Cursor :: rest =>
                varCompleter.complete(token, allTokens, line)

            case LineToken(cmd) :: Delim :: LineToken(varName) :: Delim ::
                 Cursor :: rest =>
                varValues(varName)

            case LineToken(cmd) :: Delim :: LineToken(varName) :: Delim ::
                 LineToken(partialVal) :: Cursor :: rest =>
                varValues(varName).filter(_ startsWith partialVal.toLowerCase)

            case _ =>
                Nil
        }
    }

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
class AboutHandler(val shell: SQLShell)
    extends SQLShellCommandHandler with Sorter
{
    import java.util.Properties

    val CommandName = ".about"
    val Help = "Display information about SQLShell"

    val aboutInfo = new AboutInfo

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        showFullInfo
        KeepGoing
    }

    def showAbbreviatedInfo = println(aboutInfo.identString)

    def showFullInfo =
    {
        showAbbreviatedInfo
        val buildDate = aboutInfo("build.date")
        val compiler = aboutInfo("build.compiler")
        val buildOS = aboutInfo("build.os")
        if (buildDate != null)
            println("Build date: " + buildDate);
        if (compiler != null)
            println("Built with: " + compiler);
        if (buildOS != null)
            println("Build OS:   " + buildOS);

        val javaVM = System.getProperty("java.vm.name")
        if (javaVM != null)
        {
            val buf = new StringBuilder
            buf.append(javaVM)
            val vmVersion = System.getProperty("java.vm.version")
            if (vmVersion != null)
                buf.append(" " + vmVersion)
            val vmVendor = System.getProperty("java.vm.vendor")
            if (vmVendor != null)
                buf.append(" from " + vmVendor)
            println("Running on: " + buf.toString);
        }

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

abstract class SQLHandler(val shell: SQLShell, val connection: Connection)
    extends SQLShellCommandHandler
    with Timer
    with JDBCHelper
    with CompleterHelper
{
    override def moreInputNeeded(lineSoFar: String): Boolean =
        (! lineSoFar.ltrim.endsWith(";"))

    override def complete(token: String,
                          allTokens: List[CompletionToken],
                          line: String): List[String] =
    {
        allTokens match
        {
            case Nil =>
                assert(false) // shouldn't happen
                Nil

            case LineToken(cmd) :: Cursor :: rest =>
                Nil

            case LineToken(cmd) :: Delim :: rest =>
                tokenBeforeCursor(rest) match
                {
                    case (None | Some(Delim)) =>
                        // All table names
                        shell.getTableNames(None)
                    case Some(LineToken(prefix)) =>
                        // Table names matching the prefix
                        shell.matchTableNames(None, Some(prefix))
                    case _ =>
                        shell.getTableNames(None)
                }

            case _ =>
                shell.getTableNames(None)
        }
    }
}

/**
 * The interface for a class that can consume a result set.
 */
trait ResultSetHandler
{
    def startResultSet(metadata: ResultSetMetaData, statement: String): Unit
    def handleRow(rs: ResultSet): Unit
    def endResultSet: Unit
    def closeResultSetHandler: Unit = return
}

abstract class ResultSetStringifier(showBinary: Int)
{
    private val DateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S")

    /**
     * Map a result set row, returning the each column and its name.
     *
     * @param rs       the result set
     * @param metadata the result set's metadata
     * @param rowMap   preallocated array buffer into which to dump information.
     *                 Data is indexed by (columnNumber - 1)
     *
     * @return the row map buffer, as an array
     */
    def mapRow(rs: ResultSet,
               metadata: ResultSetMetaData,
               rowMap: ArrayBuffer[String]): Array[String] =
    {
        import grizzled.io.implicits._   // for the readSome() method
        import grizzled.io.util.useThenClose

        val NULL_STR = "NULL"

        def getDateString(i: Int, dateFromResultSet: (Int) => Date): String =
        {
            val date = dateFromResultSet(i)
            if (rs.wasNull)
                NULL_STR
            else
                DateFormatter.format(date)
        }

        def clobString(i: Int): String =
        {
            if (showBinary == 0)
                "<clob>"

            else
            {
                val r = rs.getCharacterStream(i)
                if (rs.wasNull)
                    NULL_STR

                else
                {
                    useThenClose(r)
                    {
                        // Read one more than the binary length. If we get
                        // that many, then display an ellipsis.

                        val buf = r.readSome(showBinary + 1)
                        buf.length match
                        {
                            case 0 =>                     ""
                            case n if (n > showBinary) => buf.mkString("") +
                                                          "..."
                            case n =>                     buf.mkString("")
                        }
                    }
                }
            }
        }

        def binaryString(i: Int): String =
        {
            if (showBinary == 0)
                "<blob>"

            else
            {
                val is = rs.getBinaryStream(i)
                if (rs.wasNull)
                    NULL_STR

                else
                {
                    useThenClose(is)
                    {
                        // Read one more than the binary length. If we get
                        // that many, then display an ellipsis.

                        val buf = is.readSome(showBinary + 1)
                        val ellipsis = buf.length > showBinary
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
            }
        }

        def objToString(i: Int): String =
        {
            val o = rs.getObject(i)
            if (rs.wasNull)
                NULL_STR
            else
                o.toString
        }

        def colAsString(i: Int): String =
        {
            // What we'd like to do:
            /*
            rs.getObject(i)
            if (rs.wasNull) "NULL" else nonNullColAsString(i)
            */

            // Problem: With some RDBMS types (e.g., Microsoft Access, with the
            // JDBC-ODBC bridge driver), double retrieval of the column causes
            // an error. To solve this problem, we have to ensure that we
            // retrieve the column value once, which means the null test
            // has to go in multiple places.

            metadata.getColumnType(i) match
            {
                // Handle just the oddballs. getObject(i).toString should be
                // sufficient to handle the other column types.

                case SQLTypes.BINARY        => binaryString(i)
                case SQLTypes.BLOB          => binaryString(i)
                case SQLTypes.CLOB          => clobString(i)
                case SQLTypes.DATE          => getDateString(i, rs.getDate)
                case SQLTypes.LONGVARBINARY => binaryString(i)
                case SQLTypes.LONGVARCHAR   => clobString(i)
                case SQLTypes.NULL          => "<null>"
                case SQLTypes.TIME          => getDateString(i, rs.getTime)
                case SQLTypes.TIMESTAMP     => getDateString(i, rs.getTimestamp)
                case SQLTypes.VARBINARY     => binaryString(i)
                case _                      => objToString(i)
            }
        }

        // Actual function

        for {i <- 1 to metadata.getColumnCount
             colName = metadata.getColumnName(i)}
            rowMap += colAsString(i)

        rowMap.toArray
    }
}

/**
 * Encapsulates the preprocessed results from a query.
 */
private[sqlshell] class PreprocessedResults(val metadata: ResultSetMetaData,
                                            val dataFile: File)
    extends Iterable[Array[String]]
{
    import java.io.{EOFException,
                    FileInputStream,
                    FileOutputStream,
                    InputStream,
                    ObjectInputStream,
                    ObjectOutputStream,
                    Reader}

    import scala.collection.mutable.LinkedHashMap

    private val columnData = new LinkedHashMap[String, Int]
    private var totalRows = 0

    // Get the column names and initialize the associated size.

    for {i <- 1 to metadata.getColumnCount
         name = metadata.getColumnName(i)}
        columnData += (name -> name.length)

    class ResultIterator extends Iterator[Array[String]]
    {
        val in = new ObjectInputStream(new FileInputStream(dataFile))
        val buf = new ArrayBuffer[String]

        def hasNext: Boolean =
        {
            try
            {
                buf.clear
                buf ++= in.readObject.asInstanceOf[Array[String]]
                true
            }

            catch
            {
                case _: EOFException =>
                    in.close
                    buf.clear
                    false
            }
        }

        def next: Array[String] =
        {
            assert(buf.length > 0)
            buf.toArray
        }
    }

    def elements: Iterator[Array[String]] = new ResultIterator

    def rowCount = totalRows

    /**
     * Return the stored column names, in the order they appeared in
     * the result set.
     */
    def columnNamesAndSizes = columnData.readOnly

    /**
     * Save a mapped row to the specified object stream.
     *
     * @param out  the <tt>ObjectOutputStream</tt> to which to save the row
     * @param row  the data, converted to a string
     */
    def saveMappedRow(out: ObjectOutputStream, row: Array[String])
    {
        out.writeObject(row)
        totalRows += 1

        for {i <- 1 to metadata.getColumnCount
             name = metadata.getColumnName(i)}
        {
            val value = row(i - 1)
            val size = value.length
            val max = Math.max(columnData(name), size)
            columnData(name) = max
        }
    }
}

/**
 * Used by the select handler to process and cache a result set.
 */
private[sqlshell] class ResultSetCacheHandler(tempFile: File,
                                              val showBinary: Int)
    extends ResultSetStringifier(showBinary) with ResultSetHandler
{
    import java.io.{FileOutputStream, ObjectOutputStream}

    val tempOut = new ObjectOutputStream(new FileOutputStream(tempFile))
    var results: PreprocessedResults = null
    val rowMap = new ArrayBuffer[String]

    def startResultSet(metadata: ResultSetMetaData, statement: String): Unit =
        results = new PreprocessedResults(metadata, tempFile)

    def handleRow(rs: ResultSet)
    {
        rowMap.clear
        results.saveMappedRow(tempOut, mapRow(rs, results.metadata, rowMap))
    }

    def endResultSet = tempOut.close
}

/**
 * Handles SQL "SELECT" statements.
 */
class SelectHandler(shell: SQLShell, connection: Connection)
    extends SQLHandler(shell, connection) with Timer
{
    import java.io.{File, FileOutputStream, ObjectOutputStream}

    val tempFile = File.createTempFile("sqlshell", ".dat")
    tempFile.deleteOnExit

    val CommandName = "select"
    val Help = """Issue a SQL SELECT statement and display the results"""
    val ColumnSeparator = "  "

    private val resultHandlers = MutableMap.empty[String, ResultSetHandler]

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
        val fullStatement = commandName + " " + removeSemicolon(args)
        withSQLStatement(connection)
        {
            statement =>

            val (elapsed, rs) =
                time[ResultSet]
                {
                    statement.executeQuery(fullStatement)
                }

            withResultSet(rs)
            {
                dumpResults(elapsed, rs, fullStatement)
            }
        }

        KeepGoing
    }

    /**
     * Add a result handler to the list of result handlers.
     *
     * @param key     the key for the handler
     * @param handler the <tt>ResultSetHandler</tt> to add
     */
    def addResultHandler(key: String, handler: ResultSetHandler) =
    {
        assert(! (resultHandlers contains key))
        resultHandlers += (key -> handler)
    }

    /**
     * Get a result handler from the list of result handlers.
     *
     * @param key     the key for the handler
     *
     * @return <tt>Some(handler)</tt> or <tt>None</tt>
     */
    def getResultHandler(key: String): Option[ResultSetHandler] =
        resultHandlers.get(key)

    /**
     * Remove a result handler from the list of result handlers
     *
     * @param key     the key for the handler
     *
     * @return <tt>Some(handler)</tt> or <tt>None</tt>
     */
    def removeResultHandler(key: String): Option[ResultSetHandler] =
    {
        assert(resultHandlers contains key)
        val result = getResultHandler(key)
        resultHandlers -= key
        result
    }

    /**
     * Dump the results of a query.
     *
     * @param queryTime  the number of milliseconds the query took to run;
     *                   displayed with the results
     * @param rs         the JDBC result set
     * @param statement  the statement that was issued, as a string
     */
    protected def dumpResults(queryTime: Long,
                              rs: ResultSet,
                              statement: String): Unit =
    {
        logger.verbose("Processing results...")

        val metadata = rs.getMetaData
        val showBinary = shell.settings.intSetting("showbinary")
        val cacheHandler = new ResultSetCacheHandler(tempFile, showBinary)
        val handlers = cacheHandler :: resultHandlers.values.toList

        val retrievalTime =
            time
            {
                try
                {
                    for (h <- handlers)
                        h.startResultSet(metadata, statement)

                    processNextRow(rs, handlers)
                }

                finally
                {
                    for (h <- handlers)
                        h.endResultSet
                }
            }

        // Now, post-process (i.e., display) the results cached by the
        // cache handler

        val preprocessedResults = cacheHandler.results
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
            println("Retrieval time: " + formatInterval(retrievalTime))
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

        for (resultRow <- preprocessedResults)
        {
            val data =
                {for {i <- 1 to metadata.getColumnCount
                      name = metadata.getColumnName(i)
                      size = colNamesAndSizes(name)
                      fmt = columnFormats(name)}
                     yield formatter.format(fmt, resultRow(i - 1))}.toList

            println(data mkString ColumnSeparator)
        }
    }

    def processNextRow(rs: ResultSet,
                       handlers: Iterable[ResultSetHandler]): Unit =
    {
        if (rs.next)
        {
            for (h <- handlers)
                h.handleRow(rs)

            processNextRow(rs, handlers)
        }
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
 * Handles the .capture command
 */
class CaptureHandler(shell: SQLShell, selectHandler: SelectHandler)
    extends SQLShellCommandHandler
{
    val CommandName = ".capture"
    val Help =
"""|Captures the results of queries to a CSV file.
   |
   |To turn capture on:
   |
   |    .capture to /path/to/file  -- captures to specified file
   |    .capture on                -- captures to a temporary file
   |
   |To turn capture off:
   |
   |    .capture off
   |
   |Example:
   |
   |    .capture to /tmp/results.csv
   |    SELECT * from foo;
   |    SELECT * from bar;
   |    .capture off
   |
   |SQLShell opens the file for writing (truncating it, if it already exists).
   |Then, SQLShell writes each result set to the file, along with column
   |headers, until it sees ".capture off".""".stripMargin

    private val HandlerKey = this.getClass.getName
    private val showBinary = shell.settings.intSetting("showbinary")

    private class SaveToCSVHandler(path: File)
        extends ResultSetStringifier(showBinary) with ResultSetHandler
    {
        import au.com.bytecode.opencsv.CSVWriter
        import java.io.{FileOutputStream, OutputStreamWriter}

        val writer = new CSVWriter(
            new OutputStreamWriter(new FileOutputStream(path), "UTF-8")
        )
        private var metadata: ResultSetMetaData = null
        private val rowMap = new ArrayBuffer[String]

        def startResultSet(metadata: ResultSetMetaData,
                           statement: String): Unit =
        {
            this.metadata = metadata

            val headers =
                {for {i <- 1 to metadata.getColumnCount
                      name = metadata.getColumnName(i)}
                     yield name}.toArray
            writer.writeNext(headers)
        }

        def handleRow(rs: ResultSet): Unit =
        {
            rowMap.clear
            mapRow(rs, metadata, rowMap)
            writer.writeNext(rowMap.toArray)
            rowMap.clear
        }

        def endResultSet: Unit =
        {
            this.metadata = null
            writer.flush
        }

        override def closeResultSetHandler: Unit = writer.close
    }

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        args.tokenize match
        {
            case Nil =>
                usage

            case "on" :: Nil =>
                installHandler(File.createTempFile("sqlshell", ".csv"))

            case "to" :: Nil =>
                logger.error("Missing path to which to save query data.")

            case "to" :: path :: Nil =>
                installHandler(new File(path))

            case "off" :: Nil =>
                removeHandler

            case _ =>
                usage
        }

        KeepGoing
    }

    private def installHandler(path: File) =
    {
        selectHandler.getResultHandler(HandlerKey) match
        {
            case None =>
                selectHandler.addResultHandler(HandlerKey,
                                               new SaveToCSVHandler(path))
                println("Capturing result sets to: " + path.getPath)

            case handler =>
                logger.error("You're already capturing query results.")
        }
    }

    private def removeHandler =
    {
        selectHandler.removeResultHandler(HandlerKey) match
        {
            case None =>
                logger.error("You're not currently capturing query results.")

            case Some(handler) =>
                handler.closeResultSetHandler
                println("No longer capturing query results.")
        }
    }

    private def usage =
        logger.error("Usage: .capture to /path/to/file\n" +
                     "   or: .capture off")

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
                logger.error("Cannot determine autocommit status: " +
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
            logger.warning("Not in a transaction. Commit ignored.")

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
            logger.warning("Not in a transaction. Rollback ignored.")

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
                logger.error("Cannot change autocommit status: " + e.getMessage)
        }
    }

    trait NoArgChecker
    {
        protected def checkForNoArgs(commandName: String, args: String) =
        {
            if (args.trim != "")
                logger.warning("Ignoring arguments to " + commandName +
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
                logger.warning("Already in a transaction. BEGIN ignored.")
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
class UnknownHandler(shell: SQLShell,
                     val selectHandler: SelectHandler,
                     val updateHandler: UpdateHandler)
    extends SQLShellCommandHandler
{
    val CommandName = ""
    val Help = """Issue an unknown SQL statement."""

    def doRunCommand(commandName: String, args: String): CommandAction =
    {
        // Try it as both a query and an update.

        try
        {
            selectHandler.runCommand(commandName, args)
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
                logger.error("Missing the object to describe.")
            case _ =>
                logger.error("Bad .desc command: \"" + args + "\"")
        }

        KeepGoing
    }

    override def complete(token: String,
                          allTokens: List[CompletionToken],
                          line: String): List[String] =
    {
        allTokens match
        {
            case Nil =>
                assert(false) // shouldn't happen
                Nil

            case LineToken(cmd) :: Delim :: Cursor :: Nil =>
                // Command filled in (obviously, or we wouldn't be in here),
                // but first argument not.
                subCommandCompleter.complete(token, allTokens, line)

            case LineToken(cmd) :: Delim ::
                 LineToken("database") :: Cursor :: Nil =>
                Nil

            case LineToken(cmd) :: Delim ::
                 LineToken(arg) :: Delim :: Cursor :: Nil =>
                List("full")

            case LineToken(cmd) :: Delim ::
                 LineToken(arg) :: Cursor :: Nil =>
                subCommandCompleter.complete(token, allTokens, line)

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

    private def nullIfEmpty(s: String) =
        if ((s == null) || (s.trim == "")) null else s

    private def nullIfEmpty(os: Option[String]) =
        os match
        {
            case None                    => null
            case Some(s) if s == null    => null
            case Some(s) if s.trim == "" => null
            case Some(s)                 => s.trim
        }

    private def describeTable(table: String, full: Boolean) =
    {
        def getColumnDescriptions(md: ResultSetMetaData,
                                  i: Int): List[(String, String)] =
        {
            def precisionAndScale =
            {
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
                    logger.error("Can't get metadata for table " + table)
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
                        val settings = shell.settings
                        val s = nullIfEmpty(metadata.getSchemaName(1))
                        val schema =
                            if (s == null)
                                nullIfEmpty(settings.stringSetting("schema"))
                            else
                                s

                        val catalog = nullIfEmpty(metadata.getCatalogName(1))

                        // Map the table name to what the database engine
                        // thinks the table's name should be. (Necessary
                        // for Oracle.)
                        findTableName(schema, table) match
                        {
                            case None =>
                            case Some(s) =>
                                showExtraTableData(catalog, schema, s)
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
                logger.error("Too many tables match \"" + table + "\": ")
                logger.error(matching mkString ", ")
                None

            case Nil =>
                logger.error("No tables match \"" + table + "\"")
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

        try
        {
            logger.verbose("Getting primary keys for table " + table + 
                           ", catalog " + catalog + ", schema " + schema)
            val rs = dmd.getPrimaryKeys(catalog, schema, table)
            withResultSet(rs)
            {
                val columns = getPrimaryKeyColumns(rs)
                if (columns != Nil)
                    println("\nPrimary key columns: " + columns.mkString(", "))
            }
        }

        catch
        {
            case e: SQLException =>
                logger.error("Unable to retrieve primary key information: " +
                             e.getMessage)
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

        logger.verbose("Getting index information for table " + table + 
                       ", catalog " + catalog + ", schema " + schema)
        try
        {
            val rs = dmd.getIndexInfo(catalog, schema, table, false, true)
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

        catch
        {
            case e: SQLException =>
                logger.error("Unable to retrieve index information: " +
                             e.getMessage)
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

        try
        {
            logger.verbose("Getting constraint information for table " +
                           table + ", catalog " + catalog + ", schema " +
                           schema)
            val rs = dmd.getImportedKeys(catalog, schema, table)
            withResultSet(rs)
            {
                printOne(rs)
            }
        }

        catch
        {
            case e: SQLException =>
                logger.error("Unable to retrieve constraint information: " +
                             e.getMessage)
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
    private val subCommandCompleter = new ListCompleter(subCommands,
                                                        _.toLowerCase)
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
                logger.error("Missing the things to show.")
            case _ =>
                logger.error("Bad .show command: \"" + args + "\"")
        }

        KeepGoing
    }

    override def complete(token: String,
                          allTokens: List[CompletionToken],
                          line: String): List[String] =
    {
        allTokens match
        {
            case Nil =>
                assert(false) // shouldn't happen
                Nil

            case LineToken(cmd) :: Delim :: Cursor :: Nil =>
                // Command filled in (obviously, or we wouldn't be in here),
                // but first argument not.
                subCommandCompleter.complete(token, allTokens, line)

            case LineToken(cmd) :: Delim ::
                 LineToken(ShowTables(s)) :: Cursor :: Nil =>
                Nil

            case LineToken(cmd) :: Delim ::
                 LineToken(ShowTables(schema)) :: Delim :: Cursor :: Nil =>
                shell.matchTableNames(extractSchema(schema), None)

            case LineToken(cmd) :: Delim ::
                 LineToken(ShowTables(schema)) :: Delim ::
                 LineToken(tablePrefix) :: Cursor :: Nil =>
                shell.matchTableNames(extractSchema(schema), Some(tablePrefix))

            case LineToken(cmd) :: Delim ::
                 LineToken(ShowSchemas(s)) :: Cursor :: Nil =>
                Nil

            case LineToken(cmd) :: Delim ::
                 LineToken(ShowSchemas(s)) :: Delim :: Cursor :: Nil =>
                Nil

            case LineToken(cmd) :: Delim :: LineToken(arg) :: Cursor :: Nil =>
                subCommandCompleter.complete(token, allTokens, line)

            case _ =>
                Nil
        }
    }

    // Extract the schema from something matched by the ShowTables regex
    private def extractSchema(regexField: String): Option[String] =
    {
        if (regexField == null)
            None
        else if (regexField(0) == '/')
            Some(regexField drop 1)
        else
            Some(regexField)
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
