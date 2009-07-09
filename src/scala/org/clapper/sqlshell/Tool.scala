package org.clapper.sqlshell.tool

import org.clapper.sqlshell.SQLShell

import grizzled.config.Configuration
import grizzled.string.WordWrapper

class CommandLineException(message: String) extends Exception(message)

private[tool] class Params(val configFile: Option[String],
                           val dbName: Option[String],
                           val dbDriver: Option[String],
                           val dbURL: Option[String],
                           val dbUser: Option[String],
                           val dbPassword: Option[String])


private[tool] class Database(val params: Params, val config: Configuration)
{
    val connection = connect

    private def connect: java.sql.Connection =
        if (params.dbName != null)
            connectByName(params.dbName.get)
        else
            connectJDBC(params.dbDriver,
                        params.dbURL,
                        params.dbUser,
                        params.dbPassword)

    private def connectByName(dbName: String): java.sql.Connection =
    {
        def option(sectionName: String, 
                   optionName: String,
                   required: Boolean): Option[String] =
        {
            config.option(sectionName, optionName, "") match
            {
                case "" if (! required) =>
                    None

                case "" if (required) =>
                    throw new CommandLineException("Missing required \"" +
                                                   optionName + "\" option " +
                                                   "in configuration file " +
                                                   "section \"" +
                                                   sectionName + "\"")
                case s  => 
                    Some(s)
            }
        }

        val matches = matchingSections(dbName)
        val theMatch = matches match
        {
            case Nil =>
                throw new CommandLineException("No databases match \"" +
                                               dbName + "\"")

            case sectionName :: Nil =>
                connectJDBC(option(sectionName, "driver", false),
                            option(sectionName, "url", true),
                            option(sectionName, "user", false),
                            option(sectionName, "password", false))

            case _ =>
                val message = "The following database sections " +
                              "all match \"" + dbName + "\": " +
                              matches.mkString(", ")
                throw new CommandLineException(WordWrapper().wrap(message))
        }

        null
    }

    def connectJDBC(driverClassName: Option[String],
                    url: Option[String],
                    user: Option[String],
                    password: Option[String]): java.sql.Connection =
    {
        import java.sql.{DriverManager, Driver}

        val properties = new java.util.Properties
        if (user != None)
            properties.setProperty("user", user.get)
        if (password != None)
            properties.setProperty("password", password.get)

        driverClassName match
        {
            case None =>
                DriverManager.getConnection(url.get, properties)

            case Some(s) =>
                val driverClass = Class.forName(s)
                val driver = driverClass.newInstance.asInstanceOf[Driver]
                driver.connect(url.get, properties)
        }
    }

    private def matchingSections(dbName: String): List[String] =
    {
        val f = (s: String) => (s.startsWith("db_") && (s.length > 3))
        val dbSections = config.sectionNames.filter(f).toList

        def aliases(section: String): List[String] =
            config.option(section, "aliases", "").split("[,\\s]").toList

        {for {section <- dbSections
              db = section.substring(3)
              name <- (db :: aliases(section))
              if (name.startsWith(dbName))} yield section}.toList
    }
}

object Tool
{
    import grizzled.file.util.joinPath
    import joptsimple._

    val DefaultConfig = joinPath(System.getProperty("user.home"),
                                 ".sqlshell", "config")

    def main(args: Array[String]): Unit =
    {
        try
        {
            val params = parseParams(args)
            val config = Configuration(params.configFile.get)
            val db = new Database(params, config)
            new SQLShell(config, null).mainLoop
        }

        catch
        {
            case e: CommandLineException =>
                System.err.println("\n" + e.getMessage)
                System.exit(1)
        }

        System.exit(0)
    }

    private def parseParams(args: Array[String]): Params =
    {
        import java.util.Arrays.asList
        import grizzled.collection.implicits._

        val parser = new OptionParser

        parser.acceptsAll(asList("c", "config"),
                          "Specify configuration file. Defaults to: " +
                          DefaultConfig)
              .withRequiredArg
              .describedAs("config_file")
        parser.acceptsAll(asList("?", "h", "help"), "Show this usage message")
        parser.acceptsAll(asList("v", "version"), "Show version and exit")

        try
        {
            val options = parser.parse(args: _*)

            val config =
                if (options.has("c"))
                    Some(options.valueOf("c").asInstanceOf[String])
                else
                    Some(DefaultConfig)

            val positionalParams =
                (for (s <- options.nonOptionArguments) yield s).toList

            val result = positionalParams match
            {
                case Nil =>
                    throw new CommandLineException("Missing parameter(s).")

                case dbName :: Nil =>
                    new Params(config,
                               Some(dbName),
                               None,
                               None,
                               None,
                               None)

                case driver :: url :: Nil =>
                    new Params(config,
                               None,
                               Some(driver),
                               Some(url),
                               None,
                               None)

                case driver :: url :: user :: Nil =>
                    new Params(config,
                               None,
                               Some(driver),
                               Some(url),
                               Some(user),
                               None)

                case driver :: url :: user :: password :: Nil =>
                    new Params(config,
                               None,
                               Some(driver),
                               Some(url),
                               Some(user),
                               Some(password))

                case _ =>
                    throw new CommandLineException("Wrong number of "+
                                                   " parameter(s).")
            }

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

    private def printHelp(parser: OptionParser) =
    {
        println()
        println("Usage: sqlshell [OPTIONS] db")
        println("       sqlshell [OPTIONS] driver url [user [pw]]")
        parser.printHelpOn(System.err)
    }
}
