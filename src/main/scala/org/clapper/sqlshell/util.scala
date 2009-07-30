package org.clapper.sqlshell

import scala.io.Source

import grizzled.string.WordWrapper
import grizzled.string.implicits._

/**
 * Useful place to stash some sorting stuff.
 */
trait Sorter
{
    def nameSorter(a: String, b: String) = a.toLowerCase < b.toLowerCase
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
    private val itLines = source.getLines

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

