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

package org.clapper.sqlshell

import scala.collection.mutable.{Map => MutableMap}
import grizzled.string.GrizzledString._

/**
 * Trait for a class that converts a string value to something else, then
 * returns the result as an <tt>Any</tt>. This trait is broken out from
 * <tt>Setting</tt>, to permit creating subtraits that can be shared.
 */
trait ValueConverter
{
    /**
     * Convert a string value into some other type, returning it as an
     * <tt>Any</tt>.
     *
     * @param newValue the string value
     *
     * @return the converted value, as an <tt>Any</tt>
     *
     * @throws SQLShellException on conversion error
     */
    def convertString(newValue: String): Any

    /**
     * The list of legal values, as strings
     */
    val legalValues: List[String]
}

/**
 * Defines what a setting looks like.
 */
private[sqlshell] trait Setting extends ValueConverter
{
    /**
     * Get the value of a setting.
     *
     * @return the value
     */
    def get: Any

    /**
     * Set the value of a setting.
     *
     * @param newValue  the new value, already converted to whatever
     *                  real type it should be converted to
     */
    def set(newValue: Any)
}

/**
 * ValueConverter that converts from a string to a boolean.
 */
private[sqlshell] trait BooleanValueConverter extends ValueConverter
{
    val legalValues = List("true", "false", "on", "off", "0", "1")

    /**
     * Convert a string value into a boolean, returning it as an
     * <tt>Any</tt>.
     *
     * @param newValue the string value
     *
     * @return the converted boolean value, as an <tt>Any</tt>
     */
    override def convertString(newValue: String): Any =
    {
        try
        {
            import grizzled.string.util

            util.stringToBoolean(newValue)
        }

        catch
        {
            case e: IllegalArgumentException =>
                throw new SQLShellException("Cannot convert value \"" +
                                            newValue + "\" to a boolean: " +
                                            e.getMessage)

        }
    }
}

/**
 * ValueConverter that converts from a string to a integer.
 */
private[sqlshell] trait IntValueConverter extends ValueConverter
{
    val legalValues = Nil

    /**
     * The minimum legal value for the setting. Defaults to 0.
     */
    val minimum: Int = 0

    /**
     * The maximum legal value for the setting. Defaults to
     * Integer.MAX_VALUE
     */
    val maximum: Int = Integer.MAX_VALUE

    /**
     * Convert a string value into an integer, returning it as an
     * <tt>Any</tt>.
     *
     * @param newValue the string value
     *
     * @return the converted integer value, as an <tt>Any</tt>
     */
    override def convertString(newValue: String): Any =
    {
        try
        {
            val result = newValue.toInt
            if ((result < minimum) || (result > maximum))
                throw new SQLShellException("Value " + result + " is not " +
                                            "between " + minimum + " and " +
                                            maximum)
            result
        }

        catch
        {
            case e: NumberFormatException =>
                throw new SQLShellException("Cannot convert value \"" +
                                            newValue + "\" to a number.")
        }
    }
}

/**
 * A setting that stores its value internally (as opposed to retrieving it
 * from someplace).
 */
private[sqlshell] abstract class VarSetting(initialValue: Any) extends Setting
{
    private var value = initialValue

    def get = value
    def set(newValue: Any) = value = newValue
    def convertString(newValue: String): Any = newValue
}

/**
 * A setting that stores its value internally as a boolean.
 */
private[sqlshell] class BooleanSetting(initialValue: Boolean)
extends VarSetting(initialValue) with BooleanValueConverter

/**
 * A setting that stores its value internally as an integer.
 */
private[sqlshell] class IntSetting(initialValue: Int)
extends VarSetting(initialValue) with IntValueConverter

/**
 * A setting that stores its value internally as a string.
 */
private[sqlshell] class StringSetting(initialValue: String)
extends VarSetting(initialValue)
{

    val legalValues = Nil
}

/**
 * Stores the settings. The arguments are <tt>(variableName, Setting)</tt>
 * tuples. A value handler object is used, to permit both storage of local
 * values and pass-through variables whose values are obtained via other
 * means. ("autocommit" is an example of the second variable; its value is
 * stored in the JDBC driver.)
 */
private[sqlshell] class Settings(values: (String, Setting)*) extends Sorter
{
    private val settingsMap = MutableMap.empty[String, Setting]

    for ((name, handler) <- values)
        settingsMap += (name -> handler)

    /**
     * Get a list of all the variable names, in sorted order.
     *
     * @return the variable names, sorted alphabetically
     */
    def variableNames = sortByName(settingsMap.keysIterator)

    /**
     * Get list of legal values, if any, for a variable.
     *
     * @param variableName the name of the variable
     *
     * @return the list of legal values, or Nil
     */
    def legalValuesFor(variableName: String): List[String] =
    {
        if (! (settingsMap contains variableName))
            throw new UnknownVariableException("Unknown setting: \"" +
                                               variableName + "\"")
        settingsMap(variableName).legalValues
    }

    /**
     * Retrieve the value for a variable.
     *
     * @param variableName the name of the variable
     *
     * @return its value, as an <tt>Any</tt>.
     */
    def apply(variableName: String): Any =
    {
        if (! (settingsMap contains variableName))
            throw new UnknownVariableException("Unknown setting: \"" +
                                               variableName + "\"")
        settingsMap(variableName).get
    }

    /**
     * Test the value of a boolean setting. Throws an assertion failure if
     * the variable isn't a boolean.
     *
     * @param variableName the name of the variable
     *
     * @return the value of the setting
     */
    def booleanSettingIsTrue(variableName: String): Boolean =
    {
        if (! (settingsMap contains variableName))
            throw new UnknownVariableException("Unknown setting: \"" +
                                               variableName + "\"")

        val handler = settingsMap(variableName)
        handler.get.asInstanceOf[Boolean]
    }

    /**
     * Test the value of an integer setting. Throws an assertion failure if
     * the variable isn't an integer.
     *
     * @param variableName the name of the variable
     *
     * @return the value of the setting
     */
    def intSetting(variableName: String): Int =
    {
        if (! (settingsMap contains variableName))
            throw new UnknownVariableException("Unknown setting: \"" +
                                               variableName + "\"")

        val handler = settingsMap(variableName)
        handler.get.asInstanceOf[Int]
    }

    /**
     * Get a string setting.
     *
     * @param variableName the name of the variable
     *
     * @return the value of the setting
     */
    def stringSetting(variableName: String): Option[String] =
    {
        if (! (settingsMap contains variableName))
            throw new UnknownVariableException("Unknown setting: \"" +
                                               variableName + "\"")

        val handler = settingsMap(variableName)
        val sValue = handler.get.asInstanceOf[String]
        Some(sValue)
    }

    /**
     * Get a string setting.
     *
     * @param variableName the name of the variable
     * @param default      the default value to use, if the string setting
     *                     is empty or null
     *
     * @return the value of the setting, or the default
     */
    def stringSetting(variableName: String, default: String): String =
    {
        if (! (settingsMap contains variableName))
            throw new UnknownVariableException("Unknown setting: \"" +
                                               variableName + "\"")

        val handler = settingsMap(variableName)
        val sValue = handler.get.asInstanceOf[String]
        if ((sValue == null) || (sValue == ""))
            default
        else
            sValue
    }

    /**
     * Change a setting.
     *
     * @param variable  the variable name
     * @param value     the new value, as a string; it will be converted.
     */
    def changeSetting(variable: String, value: String) =
        settingsMap.get(variable) match
        {
            case None =>
                throw new UnknownVariableException("Unknown setting: \"" +
                                                   variable + "\"")

            case Some(handler) =>
                handler.set(handler.convertString(value))
        }
}
