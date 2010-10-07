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

package org.clapper.sqlshell.tool

import org.clapper.sqlshell._
import org.clapper.argot._

import grizzled.config.{Configuration, ConfigException}
import grizzled.readline.Readline.ReadlineType._
import grizzled.readline.Readline.ReadlineType

import java.io.File

/**
 * Holds the parsed parameters.
 */
private[tool] class Params(val configFile: File,
                           val dbInfo: DatabaseInfo,
                           val readlineLibs: List[ReadlineType],
                           val useAnsiColors: Boolean,
                           val showStackTraces: Boolean,
                           val verbose: Boolean,
                           val fileToRun: Option[File])
{
    def this() = this(null, null, Nil, true, false, false, None)
}

/**
 * The main program.
 */
object Tool
{
    import grizzled.file.util.joinPath
    import scala.io.Source

    val DefaultConfig = joinPath(System.getProperty("user.home"),
                                 ".sqlshell", "config")
    private val NoParams = new Params
    private val aboutInfo = new AboutInfo

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
            val source = Source.fromFile(params.configFile)
            val config = Configuration(source)
            val shell = new SQLShell(config,
                                     params.dbInfo,
                                     params.readlineLibs,
                                     params.useAnsiColors,
                                     params.showStackTraces,
                                     params.verbose,
                                     params.fileToRun)
            shell.mainLoop
        }

        catch
        {
            case e: SQLShellException =>
                System.err.println("\n" + e.getMessage)
                if (params.showStackTraces)
                    e.printStackTrace(System.err)
        }

        System.exit(0)
    }

    private def parseParams(args: Array[String]): Params =
    {
        import java.util.Arrays.asList
        import java.util.{List => JList}
        import scala.collection.JavaConversions._
        import org.clapper.argot._
        import ArgotConverters._

        val parser = new ArgotParser("sqlshell",
                                     preUsage=Some(aboutInfo.identString))

        val config = parser.option[File](
            List("c", "config"), "config_file",
            "Specify configuration file. Defaults to: " + DefaultConfig
        )
        {
            (s, opt) =>

            val f = new File(s)
            ensureFileExists(f)
            f
        }

        val noAnsi = parser.flag[Boolean](
            List("n", "no-ansi", "noansi"),
            "Disable the use of ANSI terminal sequences. This " +
            "option just sets the initial value for this " +
            "setting. The value can be changed later from " +
            "within SQLShell itself."
        )

        val readlineLibNames = parser.multiOption[String](
            List("r", "readline"), "lib_name",
            "Specify readline libraries to use. Legal values: " +
            "editline, getline, gnu, jline, simple."
        )

        val showStackTraces = parser.flag[Boolean](
            List("s", "stack"), "Show all exception stack traces."
        )

        val verbose = parser.flag[Boolean](
            List("v", "verbose"),
            "Enable various verbose messages. This option just sets the " +
            "initial verbosity value. The value can be changed later from " +
            "within SQLShell itself."
        )

        val showVersion = parser.flag[Boolean](List("V", "version"), 
                                               "Show version and exit.")
        {
            (onOff, opt) =>

            println(aboutInfo.identString)
            println(aboutInfo.copyright)
            System.exit(1)
            true
        }

        val showHelp = parser.flag[Boolean](List("?", "h", "help"),
                                            "Show this usage message.")
        {
            (onOff, opt) =>

            parser.usage()
        }

        val dbInfo = parser.parameter[DatabaseInfo](
            "db", 
            "Name of database to which to connect, or an on-the-fly database " +
            "specification, of the form:\n\n" +
            "    driver,url,[user[,password]]\n\n" +
            "If the name of a database is specified, sqlshell will look " +
            "in the configuration file for the corresponding connection " +
            "parameters. If a database specification is specified, " +
            "the specification must one argument; The driver can be a full " +
            "driver class name, or a driver alias from the configuration " +
            "file. The user and password are optional, since some databases " +
            "(like SQLite) don't require them at all.",
            optional=false)
        {
            (s, opt) =>

            s.split(",").map(_.trim).toList match
            {
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
                    throw new ArgotConversionException(
                        "Badly formatted database argument: \"" + s + "\""
                    )
            }
        }


        val runFile = parser.parameter[File](
            "@file", "Path of file of commands to run", optional=true
        )
        {
            (s, opt) =>

            if (! s.startsWith("@"))
                throw new ArgotConversionException(
                    "File \"" + s + "\" must start with an '@'."
                )

            if (s.trim.length == 1)
                throw new ArgotConversionException(
                    "Missing path after '@' in run_file argument."
                )

            val f = new File(s drop 1)
            ensureFileExists(f)
            f
        }

        try
        {
            parser.parse(args)

            // If specific readline implementations are indicated, try them.
            // Otherwise, let the library choose its own default.

            val readlineLibs = mapReadlineLibNames(readlineLibNames.value)

            val result = new Params(
                config.value.getOrElse(new File(DefaultConfig)),
                dbInfo.value.get,
                readlineLibs,
                ! noAnsi.value.getOrElse(false),
                showStackTraces.value.getOrElse(false),
                verbose.value.getOrElse(false),
                runFile.value
            )

            result
        }

        catch
        {
            case e: ArgotUsageException =>
                println(e.message)
                throw new CommandLineException(aboutInfo.name + " aborted.")

            case e: CommandLineException =>
                System.err.println(e.getMessage)
                println(parser.usageString())
                throw new CommandLineException(aboutInfo.name + " aborted.")
        }
    }

    private def ensureFileExists(f: File) =
    {
        if (! f.exists)
            throw new ArgotConversionException(
                "File \"" + f + "\" does not exist."
            )

        if (! f.isFile)
            throw new ArgotConversionException(
                "File \"" + f + "\" is not a regular file."
            )
    }

    private def mapReadlineLibNames(names: Seq[String]): List[ReadlineType] =
    {
        names.toList match
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
}
