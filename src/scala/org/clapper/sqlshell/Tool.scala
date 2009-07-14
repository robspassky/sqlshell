package org.clapper.sqlshell.tool

import org.clapper.sqlshell.SQLShell

import grizzled.config.{Configuration, ConfigException}
import grizzled.readline.Readline.ReadlineType._
import grizzled.readline.Readline.ReadlineType

private[tool] class Params(val configFile: String,
                           val dbInfo: DatabaseInfo,
                           val readlineLibs: List[ReadlineType],
                           val showStackTraces: Boolean)
{
    def this() = this(null, null, Nil, false)
}

object Tool
{
    import grizzled.file.util.joinPath
    import scala.io.Source
    import joptsimple._

    val DefaultConfig = joinPath(System.getProperty("user.home"),
                                 ".sqlshell", "config")
    private val NoParams = new Params

    def main(args: Array[String]): Unit =
    {
        val params: Params =
            try
            {
                parseParams(args)
            }

            catch
            {
                case e: CommandLineException =>
                    System.err.println("\n" + e.getMessage)
                    System.exit(1)
                    NoParams
            }

        try
        {
            val config = Configuration(Source.fromFile(params.configFile))
            val shell = new SQLShell(config, 
                                     params.dbInfo, 
                                     params.readlineLibs,
                                     params.showStackTraces)
            shell.mainLoop
        }

        catch
        {
            case e: SQLShellException =>
                System.err.println("\n" + e.getMessage)
                if (params.showStackTraces)
                    e.printStackTrace(System.err)
                System.exit(1)
        }

        System.exit(0)
    }

    private def parseParams(args: Array[String]): Params =
    {
        import java.util.Arrays.asList
        import java.util.{List => JList}
        import grizzled.collection.implicits._

        val parser = new OptionParser

        parser.acceptsAll(asList("c", "config"),
                          "Specify configuration file. Defaults to: " +
                          DefaultConfig)
              .withRequiredArg
              .describedAs("config_file")
        parser.acceptsAll(asList("r", "readline"),
                          "Specify readline libraries to use. Legal values: " +
                          "editline, getline, gnu, jline, simple. May be " +
                          "specified more than once.")
              .withRequiredArg
              .describedAs("lib_name")
        parser.acceptsAll(asList("s", "stack"),
                          "Show all exception stack traces.")
        parser.acceptsAll(asList("?", "h", "help"), "Show this usage message")
        parser.acceptsAll(asList("v", "version"), "Show version and exit")

        try
        {
            val options = parser.parse(args: _*)

            val config =
                if (options.has("c"))
                    options.valueOf("c").asInstanceOf[String]
                else
                    DefaultConfig

            val showStackTraces = options.has("s")

            val positionalParams =
                (for (s <- options.nonOptionArguments) yield s).toList

            val readlineLibNames =
                if (options.has("r"))
                    {for (v <- options.valuesOf("r")
                                      .asInstanceOf[JList[String]])
                         yield(v)}.toList
                else
                    Nil

            val readlineLibs = mapReadlineLibNames(readlineLibNames)

            val dbInfo = positionalParams match
            {
                case Nil =>
                    throw new CommandLineException("Missing parameter(s).")

                case dbName :: Nil =>
                    new DatabaseInfo(Some(dbName))

                case driver :: url :: Nil =>
                    new DatabaseInfo(Some(driver), Some(url), None, None)

                case driver :: url :: user :: Nil =>
                    new DatabaseInfo(Some(driver), Some(url), Some(user), None)

                case driver :: url :: user :: password :: Nil =>
                    new DatabaseInfo(Some(driver),
                                     Some(url),
                                     Some(user),
                                     Some(password))

                case _ =>
                    throw new CommandLineException("Wrong number of "+
                                                   " parameter(s).")
            }

            val result = new Params(config,
                                    dbInfo,
                                    readlineLibs,
                                    showStackTraces)

            val abort = options.has("?") || options.has("v")
            if (options.has("v"))
                println(Ident.IdentString)

            if (options.has("?"))
                printHelp(parser)

            if (abort)
                System.exit(1)

            result
        }

        catch
        {
            case e: OptionException =>
                System.err.println(e.getMessage)
                printHelp(parser)
                throw new CommandLineException(Ident.Name + " aborted.")

            case e: CommandLineException =>
                System.err.println(e.getMessage)
                printHelp(parser)
                throw new CommandLineException(Ident.Name + " aborted.")
        }
    }

    private def mapReadlineLibNames(names: List[String]): List[ReadlineType] =
    {
        names match
        {
            case name :: tail =>
                val lib = name match
                {
                    case "gnu"      => ReadlineType.GNUReadline
                    case "editline" => ReadlineType.EditLine
                    case "getline"  => ReadlineType.GetLine
                    case "jline"    => ReadlineType.JLine
                    case "simple"   => ReadlineType.Simple
                    case bad        => throw new CommandLineException(
                                           "Unknown readline type: \"" + bad +
                                           "\"")
                }
                lib :: mapReadlineLibNames(tail)

            case Nil => Nil
        }
    }

    private def printHelp(parser: OptionParser) =
    {
        println()
        println("Usage: sqlshell [OPTIONS] db")
        println("       sqlshell [OPTIONS] driver url [user [pw]]")
        parser.printHelpOn(System.err)
    }
}
