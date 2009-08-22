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

import org.clapper.sqlshell.SQLShell

import grizzled.config.{Configuration, ConfigException}
import grizzled.readline.Readline.ReadlineType._
import grizzled.readline.Readline.ReadlineType

import java.io.File

/**
 * Holds the parsed parameters.
 */
private[tool] class Params(val configFile: String,
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
    import joptsimple._

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
            val config = Configuration(Source.fromFile(params.configFile))
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
        parser.acceptsAll(asList("n", "no-ansi", "noansi"),
                          "Disable the use of ANSI terminal sequences. This " +
                          "option just sets the initial value for this " +
                          "setting. The value can be changed later from " +
                          "within SQLShell itself.")
        parser.acceptsAll(asList("r", "readline"),
                          "Specify readline libraries to use. Legal values: " +
                          "editline, getline, gnu, jline, simple. May be " +
                          "specified more than once.")
              .withRequiredArg
              .describedAs("lib_name")
        parser.acceptsAll(asList("s", "stack"),
                          "Show all exception stack traces.")
        parser.acceptsAll(asList("v", "verbose"),
                          "Enable various verbose messages. This option just " +
                          "sets the initial value for this setting. The " +
                          "value can be changed later from within SQLShell " +
                          "itself.")
        parser.acceptsAll(asList("V", "version"), "Show version and exit.")
        parser.acceptsAll(asList("?", "h", "help"), "Show this usage message.")

        try
        {
            val options = parser.parse(args: _*)

            val config =
                if (options.has("c"))
                    options.valueOf("c").asInstanceOf[String]
                else
                    DefaultConfig

            val showStackTraces = options.has("s")
            val verbose = options.has("v")
            val showAnsi = ! options.has("n")

            val positionalParams =
                (for (s <- options.nonOptionArguments) yield s).toList

            if (positionalParams.length == 0)
                throw new CommandLineException("Missing parameter(s).")

            val (dbParams, path) =
                if (positionalParams.last.startsWith("@"))
                    // Extract @file argument.
                    (positionalParams.slice(0, positionalParams.length - 1),
                     Some(positionalParams.last.substring(1)))
                else
                    (positionalParams, None)

            val fileToRun =
                if (path == None)
                    None
                else
                {
                    if (path.get.length == 0)
                        throw new CommandLineException("Missing path after " +
                                                       "\"@\".")
                    val f = new File(path.get)
                    if (! f.exists)
                        throw new CommandLineException("File \"" + f.getPath +
                                                       "\" does not exist.")
                    Some(f)
                }

            val readlineLibNames =
                if (options.has("r"))
                    {for (v <- options.valuesOf("r")
                                      .asInstanceOf[JList[String]])
                         yield(v)}.toList
                else
                    Nil

            val readlineLibs = mapReadlineLibNames(readlineLibNames)

            val dbInfo = dbParams match
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
                                    showAnsi,
                                    showStackTraces,
                                    verbose,
                                    fileToRun)

            val abort = options.has("?") || options.has("version")
            if (options.has("version"))
                aboutInfo.identString

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
                throw new CommandLineException(aboutInfo.name + " aborted.")

            case e: CommandLineException =>
                System.err.println(e.getMessage)
                printHelp(parser)
                throw new CommandLineException(aboutInfo.name + " aborted.")
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
        println("Usage: sqlshell [OPTIONS] db [@file]")
        println("       sqlshell [OPTIONS] driver url [user [pw]] [@file]")
        parser.printHelpOn(System.err)
    }
}
