package org.clapper.sqlshell

import scala.collection.mutable.{Map => MutableMap}
import grizzled.string.implicits._

private[sqlshell] trait ValueConverter
{
    def convertString(newValue: String): Any
}

private[sqlshell] trait Setting extends ValueConverter
{
    def get: Any
    def set(newValue: Any)
}

private[sqlshell] trait BooleanValueConverter extends ValueConverter
{
    override def convertString(newValue: String): Any =
    {
        try
        {
            val boolValue: Boolean = newValue
            boolValue
        }

        catch
        {
            case e: IllegalArgumentException =>
                error("Cannot convert value \"" + newValue +
                      "\" to a boolean: " + e.getMessage)
        }
    }
}

private[sqlshell] trait IntValueConverter extends ValueConverter
{
    override def convertString(newValue: String): Any =
    {
        try
        {
            newValue.toInt
        }

        catch
        {
            case e: NumberFormatException =>
                error("Cannot convert value \"" + newValue +
                      "\" to an integer: " + e.getMessage)
        }
    }
}

/**
 * A setting that stores its value internally (as opposed to retrieving it
 * from someplace).
 */
private[sqlshell] class VarSetting(initialValue: Any)
    extends Setting
{
    private var value = initialValue

    def get = value
    def set(newValue: Any) = value = newValue
    def convertString(newValue: String): Any = newValue
}

private[sqlshell] class BooleanSetting(initialValue: Boolean)
    extends VarSetting(initialValue) with BooleanValueConverter

private[sqlshell] class IntSetting(initialValue: Int)
    extends VarSetting(initialValue) with IntValueConverter

/**
 * Handles string settings.
 */
private[sqlshell] class StringSetting(initialValue: String)
    extends VarSetting(initialValue)

/**
 * Stores the settings. The argument is a list of
 * <tt>(variableName, SettingType, valueHandler)</tt> tuples.
 * A value handler object is used, to permit both storage of local values
 * and pass-through variables whose values are obtained via other means.
 * ("autocommit" is an example of the second variable; its value is stored
 * in the JDBC driver.)
 */
private[sqlshell] class Settings(values: (String, Setting)*)
    extends Sorter
{
    private val settingsMap = MutableMap.empty[String, Setting]

    for ((name, handler) <- values)
        settingsMap += (name -> handler)

    def variableNames = settingsMap.keys.toList.sort(nameSorter)

    def apply(variableName: String): Any =
    {
        assert(settingsMap contains variableName)
        settingsMap(variableName).get
    }

    def booleanSettingIsTrue(variableName: String): Boolean =
    {
        assert(settingsMap contains variableName)

        val handler = settingsMap(variableName)
        handler.get.asInstanceOf[Boolean]
    }

    def intSetting(variableName: String): Int =
    {
        assert(settingsMap contains variableName)

        val handler = settingsMap(variableName)
        handler.get.asInstanceOf[Int]
    }

    def stringSetting(variableName: String): Option[String] =
    {
        assert(settingsMap contains variableName)

        val handler = settingsMap(variableName)
        val sValue = handler.get.asInstanceOf[String]
        Some(sValue)
    }

    def untypedSetting(variableName: String): Any =
    {
        assert(settingsMap contains variableName)

        val handler = settingsMap(variableName)
        handler.get.toString
    }

    def settingValueToString(variableName: String) =
    {
        settingsMap.get(variableName) match
        {
            case None =>
                throw new UnknownVariableException("Unknown setting: \"" +
                                                   variableName + "\"")

            case Some(handler) =>
                variableName + "=" + handler.get.toString
        }
    }

    def changeSetting(variable: String, value: String) =
    {
        settingsMap.get(variable) match
        {
            case None =>
                throw new UnknownVariableException("Unknown setting: \"" +
                                                   variable + "\"")

            case Some(handler) =>
                handler.set(handler.convertString(value))
        }
    }
}
