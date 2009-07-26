package org.clapper.sqlshell

import grizzled.string.WordWrapper
import grizzled.config.Configuration

import java.sql.{Connection => SQLConnection, 
                 SQLException,
                 Driver => JDBCDriver,
                 DriverManager => JDBCDriverManager}

/**
 * Contains information (parsed from the command line or the user) about the
 * database to which to connect
 */
private[sqlshell] class DatabaseInfo(val dbName: Option[String],
                                     val dbDriver: Option[String],
                                     val dbURL: Option[String],
                                     val dbUser: Option[String],
                                     val dbPassword: Option[String])
{
    def this() = this(None, None, None, None, None)

    def this(dbName: Option[String]) =
        this(dbName, None, None, None, None)

    def this(dbDriver: Option[String],
             dbURL: Option[String],
             dbUser: Option[String],
             dbPassword: Option[String]) =
        this(None, dbDriver, dbURL, dbUser, dbPassword)
}

/**
 * Connection info.
 *
 * @param connection  the established connection
 * @param configInfo  the configuration options associated with the
 *                    connection
 */
private[sqlshell] class ConnectionInfo(val connection: SQLConnection,
                                       val configInfo: Map[String, String])
{
}

/**
 * Handles connecting to a database.
 */
private[sqlshell] class DatabaseConnector(val config: Configuration)
{
    def connect(info: DatabaseInfo): ConnectionInfo =
    {
        if (info.dbName != null)
            connectByName(info.dbName.get)

        else
        {
            // The driver name might be an alias. If it is, get the real
            // class name.

            if (info.dbDriver == None)
                throw new SQLShellException("JDBC driver name cannot be null.")

            if (info.dbURL == None)
                throw new SQLShellException("JDBC URL cannot be null.")

            val conn = connectJDBC(getDriver(info.dbDriver.get),
                                   info.dbURL.get,
                                   info.dbUser,
                                   info.dbPassword)
            val options = Map("driver" -> optionToString(info.dbDriver),
                              "url" -> optionToString(info.dbURL),
                              "user" -> optionToString(info.dbURL),
                              "password" -> optionToString(info.dbPassword))
            new ConnectionInfo(conn, options)
        }
    }

    private def connectByName(dbName: String): ConnectionInfo =
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
                    throw new SQLShellConfigException(
                        "Missing required \"" + optionName + "\" option " +
                        "in configuration file section \"" + sectionName + "\"")

                case s  => 
                    Some(s)
            }
        }

        val matches = matchingSections(dbName)
        matches match
        {
            case Nil =>
                throw new SQLShellException("No databases match \"" +
                                            dbName + "\"")

            case sectionName :: Nil =>
                // The driver name might be an alias. If it is, get the real
                // class name.
                val driverOption = option(sectionName, "driver", true)
                val driverClassName = getDriver(driverOption.get)
                val conn = connectJDBC(driverClassName,
                                       option(sectionName, "url", true).get,
                                       option(sectionName, "user", false),
                                       option(sectionName, "password", false))
                new ConnectionInfo(conn, config.options(sectionName))

            case _ =>
                val message = "The following database sections " +
                              "all match \"" + dbName + "\": " +
                              matches.mkString(", ")
                throw new SQLShellException(WordWrapper().wrap(message))
        }
    }

    def connectJDBC(driverClassName: String,
                    url: String,
                    user: Option[String],
                    password: Option[String]): SQLConnection =
    {
        val properties = new java.util.Properties
        if (user != None)
            properties.setProperty("user", user.get)
        if (password != None)
            properties.setProperty("password", password.get)

        try
        {
            val cls = Class.forName(driverClassName)
            val driver = cls.newInstance.asInstanceOf[JDBCDriver]
            val connection = driver.connect(url, properties)

            // SQLite3 driver returns a null connection on connection
            // failure.
            if (connection == null)
                throw new SQLShellException("Cannot connect to \"" + url + "\"")
            connection
        }

        catch
        {
            case e: ClassNotFoundException =>
                val ex = new SQLShellException("JDBC driver class " +
                                               "\"" + driverClassName +
                                               "\" not found.")
                ex.initCause(e)
                throw ex
        }
    }

    private def getDriver(driverName: String): String =
    {
        val configDriverClass = config.option("drivers", driverName, null)
        if (configDriverClass != null) configDriverClass else driverName
    }

    private def optionToString(option: Option[String]): String =
    {
        option match
        {
            case None    => ""
            case Some(s) => s
        }
    }

    private def matchingSections(dbName: String): List[String] =
    {
        val f = (s: String) => (s.startsWith("db_") && (s.length > 3))
        val dbSections = config.sectionNames.filter(f).toList

        def aliases(section: String): List[String] =
            config.option(section, "aliases", "").split("[,\\s]").toList

        val set = Set.empty[String] ++
                  {for {section <- dbSections
                        db = section.substring(3)
                        name <- (db :: aliases(section))
                        if (name.startsWith(dbName))} yield section}
        set.toList
    }
}