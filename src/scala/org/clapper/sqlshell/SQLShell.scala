package org.clapper.sqlshell

import grizzled.cmd._
import grizzled.GrizzledString._
import grizzled.config.Configuration
import java.sql._

object Ident
{
    val Version = "0.1"
    val Name = "sqlshell"
    val Copyright = "Copyright (c) 2009 Brian M. Clapper. All rights reserved."

    val IdentString = "%s, version %s\n%s" format (Name, Version, Copyright)
}

class SQLShell(val config: Configuration, val connection: Connection)
    extends CommandInterpreter("sqlshell")
{
    val handlers = List(new HistoryHandler(this),
                        new RedoHandler(this))

    override def preLoop =
    {
        println(Ident.IdentString)
        println("Using " + readline + " readline implementation.")
    }

    override def postLoop =
    {
    }

    override def preCommand(line: String) =
        if (line.ltrim.startsWith("--"))
            ""
        else
            line
}
