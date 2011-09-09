/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2009-2011, Brian M. Clapper
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

/** Contains information about the utility. The available keys are defined in
  * the project build script.
  */
class AboutInfo {
  import java.util.Properties

  private val aboutInfo: Properties = loadAboutInfo

  /** Get information about the specified key.
    *
    * @param key  the key
    *
    * @return the associated value wrapped in `Some`, or
    *         `None` if not found.
    */
  def apply(key: String): Option[String] = get(key)

  /** Convenience method to get the identification string for the program.
    *
    * @return the identification string
    */
  def identString: String =
    "%s, version %s (%s)" format (name, version, buildTimestamp)

  /** Get the copyright string.
    *
    * @return the copyright string
    */
  val copyright = "Copyright (c) 2009-2011 Brian M. Clapper"

  /** Convenience method to get the program name.
    *
    * @return the program name
    */
  def name = get("sqlshell.name").get

  /** Convenience method to get the build date and time, as a string
    *
    * @return the build date and time
    */
  def buildTimestamp = get("build.timestamp").get

  /** Convenience method to get the program version.
    *
    * @return the program name
    */
  def version = get("sqlshell.version").get

  /** Retrieves the current Java VM.
    *
    * @return the Java VM identification string
    */
  def javaVirtualMachine = get("java.vm")

  private def get(key: String): Option[String] = {
    key match {
      case "java.vm" => getJavaVM
      case _         => getAboutInfoProperty(key)
    }
  }

  private def getAboutInfoProperty(key: String) = {
    aboutInfo.getProperty(key) match {
      case null          => None
      case value: String => Some(value)
    }
  }

  private def getJavaVM = {
    val javaVM = System.getProperty("java.vm.name")
    if (javaVM != null) {
      val buf = new StringBuilder
      buf.append(javaVM)
      val vmVersion = System.getProperty("java.vm.version")
      if (vmVersion != null)
        buf.append(" " + vmVersion)
      val vmVendor = System.getProperty("java.vm.vendor")
      if (vmVendor != null)
        buf.append(" from " + vmVendor)
      Some(buf.toString)
    }

      else {
        None
      }
  }

  private def loadAboutInfo = {
    val classLoader = getClass.getClassLoader
    val AboutInfoURL = classLoader.getResource(
      "org/clapper/sqlshell/SQLShell.properties")

    val aboutInfo = new Properties
    if (AboutInfoURL != null) {
      val is = AboutInfoURL.openStream
      try {
        aboutInfo.load(is)
      }

      finally {
        is.close
      }
    }

    aboutInfo
  }
}
