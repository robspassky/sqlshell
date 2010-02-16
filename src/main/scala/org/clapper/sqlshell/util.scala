package org.clapper.sqlshell

import scala.io.Source

import grizzled.string.WordWrapper
import grizzled.string.implicits._

/**
 * Useful place to stash some sorting stuff.
 */
trait Sorter
{
    def sortByName(list: List[String]): List[String] =
        list.sortWith {_.toLowerCase < _.toLowerCase}

    def sortByName(it: Iterator[String]): List[String] = sortByName(it.toList)
}

/**
 * Convenient wrapper functions
 */
trait Wrapper
{
    val wordWrapper = new WordWrapper

    def wrapPrintln(s: String) = println(wordWrapper.wrap(s))

    def wrapPrintf(fmt: String, args: Any*) =
        println(wordWrapper.wrap(fmt format(args: _*)))
}

/**
 * Timer mix-in.
 */
trait Timer
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
     * Takes a fragment of code, executes it, and returns how long it took
     * (in real time) to execute.
     *
     * @param block  block of code to run
     *
     * @return the number of milliseconds it took to run the block
     */
    def time(block: => Unit): Long =
    {
        val start = System.currentTimeMillis
        block
        System.currentTimeMillis - start
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
 * Wraps a source in something that can be pushed onto the command
 * interpreter's reader stack.
 */
class SourceReader(source: Source)
{
    // lines iterator
    private val itLines = source.getLines()

    /**
     * Read the next line of input.
     *
     * @param prompt  the prompt (ignored)
     *
     * @return <tt>Some(line)</tt> with the next input line, or <tt>None</tt>
     *         on EOF.
     */
    def readline(prompt: String): Option[String] =
        if (itLines.hasNext)
            Some(itLines.next.chomp)
        else
            None
}

/**
 * Contains information about the utility. The available keys are defined in
 * the project build script.
 */
class AboutInfo
{
    import java.util.Properties

    private val aboutInfo: Properties = loadAboutInfo

    /**
     * Get information about the specified key.
     *
     * @param key  the key
     *
     * @return the associated value wrapped in <tt>Some</tt>, or
     *         <tt>None</tt> if not found.
     */
    def apply(key: String): Option[String] = get(key)

    /**
     * Convenience method to get the identification string for the program.
     *
     * @return the identification string
     */
    def identString: String =
        "%s, version %s (%s)" format (name, version, buildTimestamp)

    /**
     * Get the copyright string.
     *
     * @return the copyright string
     */
    val copyright = "Copyright (c) 2009-2010 Brian M. Clapper"

    /**
     * Convenience method to get the program name.
     *
     * @return the program name
     */
    def name = get("sqlshell.name").get

    /**
     * Convenience method to get the build date and time, as a string
     *
     * @return the build date and time
     */
    def buildTimestamp = get("build.timestamp").get

    /**
     * Convenience method to get the program version.
     *
     * @return the program name
     */
    def version = get("sqlshell.version").get

    /**
     * Retrieves the current Java VM.
     *
     * @return the Java VM identification string
     */
    def javaVirtualMachine = get("java.vm")

    private def get(key: String): Option[String] =
    {
        val s = 
            key match
            {
                case "java.vm" => getJavaVM
                case _         => aboutInfo.getProperty(key)
            }

        if (s == null)
            None
        else
            Some(s)
    }

    private def getJavaVM =
    {
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
            buf.toString
        }
        else
        {
            null
        }
    }

    private def loadAboutInfo =
    {
        val classLoader = getClass.getClassLoader
        val AboutInfoURL = classLoader.getResource(
            "org/clapper/sqlshell/SQLShell.properties")

        val aboutInfo = new Properties
        if (AboutInfoURL != null)
        {
            val is = AboutInfoURL.openStream
            try
            {
                aboutInfo.load(is)
            }

            finally
            {
                is.close
            }
        }

        aboutInfo
    }
}
