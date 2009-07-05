import grizzled.cmd._
import grizzled.string.implicits._

class SQLShell extends CommandInterpreter("sqlshell")
{
    private var Version = "0.1"
    private var Name = "sqlshell"
    private var Copyright = "Copyright (c) 2009 Brian M. Clapper. " +
                            "All rights reserved."

    val handlers = List(new HistoryHandler(this),
                        new RedoHandler(this))

    override def preLoop =
    {
        println(Name + ", version " + Version)
        println(Copyright)
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

    def main(args: Array[String]): Unit =
        mainLoop
}
