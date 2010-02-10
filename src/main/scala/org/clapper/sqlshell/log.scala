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

package org.clapper.sqlshell.log

import org.clapper.sqlshell._
import grizzled.string.WordWrapper

abstract sealed class LogLevel(val value: Int)
{
    def matches(s: String): Boolean = s.toLowerCase == toString
}

case object Debug extends LogLevel(40)
{
    override def toString = "debug"
}

case object Verbose extends LogLevel(30)
{
    override def toString = "verbose"
}

case object Info extends LogLevel(20)
{
    override def toString = "info"
}

case object Warning extends LogLevel(10)
{
    override def toString = "warning"
}

case object Error extends LogLevel(0)
{
    override def toString = "error"
}

/**
 * Simple messaging/logging singleton.
 */
object logger
{
    private var theLevel: LogLevel = Info
    var useAnsi = true

    private val wrapper = new WordWrapper(79)
    val Levels = List(Error, Warning, Info, Verbose, Debug)

    def level = theLevel

    def level_=(newLevel: Any): Unit =
    {
        newLevel match
        {
            case l: LogLevel =>
                theLevel = l

            case i: Int =>
                val l = Levels.filter(_.value == i)
                if (l == Nil)
                    throw new SQLShellException("Bad log level: " + i)
                theLevel = l(0)

            case s: String =>
                val l = Levels.filter(_.matches(s))
                if (l == Nil)
                    throw new SQLShellException("Bad log level: " + s)
                theLevel = l(0)

            case _ =>
                throw new SQLShellException("Bad log level: " + newLevel)
        }
    }

    /**
     * Emit a message only if the log level is set to Debug.
     *
     * @param msg  the message
     */
    def debug(msg: => String) =
        if (level.value >= Debug.value)
            emit("[DEBUG] " + msg, Console.GREEN + Console.BOLD)

    /**
     * Emit a message only if the log level is set to Verbose or above.
     *
     * @param msg  the message
     */
    def verbose(msg: => String) =
        if (level.value >= Verbose.value)
            emit(msg, Console.BLUE + Console.BOLD)

    /**
     * Emit a message only if the log level is set to Info or above.
     *
     * @param msg  the message
     */
    def info(msg: => String) =
        if (level.value >= Info.value)
            emit(msg, "")

    /**
     * Emit a message only if the log level is set to Warning or above.
     *
     * @param msg  the message
     */
    def warning(msg: => String) =
        if (level.value >= Warning.value)
            emit("Warning: " + msg, Console.YELLOW)

    /**
     * Emit an error message. These cannot be suppressed.
     *
     * @param msg  the message
     */
    def error(msg: => String) =
        emit("Error: " + msg, Console.RED + Console.BOLD)

    private def emit(msg: String, ansiModifiers: => String) =
    {
        val wrappedMsg = wrapper.wrap(msg)
        if (useAnsi)
            println(ansiModifiers + wrappedMsg + Console.RESET)
        else
            println(wrappedMsg)
    }
}
