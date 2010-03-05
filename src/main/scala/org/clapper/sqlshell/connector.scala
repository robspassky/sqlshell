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

/**
 * Database connection stuff.
 */
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
 * Database metadata.
 */
private[sqlshell] class DatabaseMetadata(val productName: Option[String],
                                         val productVersion: Option[String],
                                         val driverName: Option[String],
                                         val driverVersion: Option[String],
                                         val user: Option[String],
                                         val jdbcURL: Option[String],
                                         val isolation: String)

/**
 * Connection info.
 *
 * @param connection         the established connection
 * @param configInfo         the configuration options associated with the
 *                           connection
 * @param configSectionName  the configuration section name
 * @parma jdbcURL            the JDBC URL for the database
 */
private[sqlshell] class ConnectionInfo(val connection: SQLConnection,
                                       val configInfo: Map[String, String],
                                       configSectionName: Option[String],
                                       val jdbcURL: String)
{
    private val DbSectionName = """^db_(.*)$""".r

    /**
     * Get the DB name, which is the section name, with "db_" stripped off.
     * Returns None if name is unknown.
     */
    val dbName = configSectionName match
    {
        case None                   => None
        case Some(DbSectionName(s)) => Some(s)
        case Some(s)                => Some(s)
    }

    def databaseInfo: DatabaseMetadata =
    {
        def toOption(s: String): Option[String] =
            if ((s == null) || (s.trim == "")) None else Some(s)

        val metadata = connection.getMetaData

        val isolation =
            connection.getTransactionIsolation match
            {
                case SQLConnection.TRANSACTION_READ_UNCOMMITTED =>
                    "read uncommitted"
                case SQLConnection.TRANSACTION_READ_COMMITTED =>
                    "read committed"
                case SQLConnection.TRANSACTION_REPEATABLE_READ =>
                    "repeatable read"
                case SQLConnection.TRANSACTION_SERIALIZABLE =>
                    "serializable"
                case SQLConnection.TRANSACTION_NONE =>
                    "none"
                case n =>
                    "unknown transaction isolation value of " + n.toString
            }

        new DatabaseMetadata(toOption(metadata.getDatabaseProductName),
                             toOption(metadata.getDatabaseProductVersion),
                             toOption(metadata.getDriverName),
                             toOption(metadata.getDriverVersion),
                             toOption(metadata.getUserName),
                             toOption(metadata.getURL),
                             isolation)
    }
}

/**
 * Handles connecting to a database.
 */
private[sqlshell] class DatabaseConnector(val config: Configuration)
{
    /**
     * Connect to the database specified in a <tt>DatabaseInfo</tt>
     * object, consulting the configuration, if necessary.
     *
     * @param info  the database information
     *
     * @return a <tt>ConnectionInfo</tt> object, containing the connection
     *         and the configuration data for the database
     */
    def connect(info: DatabaseInfo): ConnectionInfo =
    {
        if (info.dbName != None)
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
            new ConnectionInfo(conn, options, None, info.dbURL.get)
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
                        "in configuration file section \"" + sectionName +
                        "\"")

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
                val url = option(sectionName, "url", true).get
                val conn = connectJDBC(driverClassName,
                                       url,
                                       option(sectionName, "user", false),
                                       option(sectionName, "password", false))
                new ConnectionInfo(conn, config.options(sectionName), 
                                   Some(sectionName), url)

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
                throw new SQLShellException("Can't connect to \"" + url + "\"")
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
