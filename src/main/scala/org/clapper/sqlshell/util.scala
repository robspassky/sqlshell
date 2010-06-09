/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2009-2010, Brian M. Clapper
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

package org.clapper.sqlshell

import scala.io.Source

import grizzled.string.WordWrapper
import grizzled.string.GrizzledString._

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

    def wrapPrintln(prefix: String, s: String) =
        println(new WordWrapper(prefix=prefix).wrap(s))

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

object util
{
    def isEmpty(s: String) = (s == null) || (s.trim == "")
    def nullIfEmpty(s: String) = if (isEmpty(s)) null else s

    def nullIfEmpty(os: Option[String]) =
        os match
        {
            case None                  => null
            case Some(s) if isEmpty(s) => null
            case Some(s)               => s.trim
        }

    def noneIfNull(s: String) = if (s == null) None else Option(s)

    def noneIfEmpty(s: String) = if (isEmpty(s)) None else Option(s)

    def defaultIfEmpty(s: String, default: String) =
        if (isEmpty(s)) default else s
}
