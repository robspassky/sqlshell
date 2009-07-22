package org.clapper.sqlshell

import scala.collection.mutable.{Map => MutableMap}

/**
 * An enumeration, of sorts, for "settings" types.
 */
abstract sealed class SettingType
case object IntSetting extends SettingType
case object StringSetting extends SettingType
case object BooleanSetting extends SettingType

/**
 * Stores the settings. The argument is a list of
 * <tt>(variableName, SettingType, initialValue)</tt> tuples.
 */
private[sqlshell] class Settings(values: (String, SettingType, Any)*)
    extends Sorter
{
    private val settingsMap = MutableMap.empty[String, (SettingType, Any)]

    for ((name, settingType, value) <- values)
        settingsMap += (name -> (settingType, value))

    def variableNames = settingsMap.keys.toList.sort(ascSorter)

    def apply(variableName: String): (SettingType, Any) =
    {
        assert(settingsMap contains variableName)
        settingsMap(variableName)
    }

    def booleanSettingIsTrue(variableName: String): Boolean =
    {
        assert(settingsMap contains variableName)

        val (valueType, value) = settingsMap(variableName)
        assert(valueType == BooleanSetting)
        value.asInstanceOf[Boolean]
    }

    def intSetting(variableName: String): Int =
    {
        assert(settingsMap contains variableName)

        val (valueType, value) = settingsMap(variableName)
        assert(valueType == IntSetting)
        value.asInstanceOf[Int]
    }

    def stringSetting(variableName: String): Option[String] =
    {
        assert(settingsMap contains variableName)

        val (valueType, value) = settingsMap(variableName)
        assert(valueType == StringSetting)
        val sValue = value.asInstanceOf[String]
        Some(sValue)
    }

    def settingValueToString(variableName: String) =
    {
        settingsMap.get(variableName) match
        {
            case None =>
                throw new UnknownVariableException("Unknown setting: \"" + 
                                                   variableName + "\"")

            case Some(tuple) =>
                variableName + "=" + tuple._2
        }
    }

    def changeSetting(variable: String, value: String) =
    {
        settingsMap.get(variable) match
        {
            case None =>
                throw new UnknownVariableException("Unknown setting: \"" + 
                                                   variable + "\"")

            case Some(tuple) =>
                convertAndStoreSetting(variable, value, tuple._1)
        }
    }

    private def convertAndStoreSetting(variable: String, 
                                       value: String, 
                                       valueType: SettingType) =
    {
        import grizzled.string.implicits._

        valueType match
        {
            case IntSetting =>
                try
                {
                    settingsMap(variable) = (valueType, value.toInt)
                }

                catch
                {
                    case e: NumberFormatException =>
                        error("Attempt to set \"" + variable + "\" to \"" +
                              value + "\" failed: Bad numeric value.")
                }

            case BooleanSetting =>
                try
                {
                    val boolValue: Boolean = value
                    settingsMap(variable) = (valueType, boolValue)
                }

                catch
                {
                    case e: IllegalArgumentException =>
                        error("Attempt to set \"" + variable + "\" to \"" +
                              value + "\" failed: " + e.getMessage)
                }

            case StringSetting =>
                settingsMap(variable) = (valueType, value)

            case _ =>
                assert(false)
        }
    }
}
