package sqlshell.build

import grizzled.file.{util => FileUtil}
import grizzled.{sys => SysUtil}
import java.io.{File, FileOutputStream}
import java.util.{Date, Properties}
import java.text.SimpleDateFormat
import sbt._
import sbt.Keys._

/** Tasks and defs that cannot be implemented in build.sbt
  *
  */
object defs {

  val SQLShell = config("sqlshell")

  val aboutInfo = TaskKey[Unit]("create-about-info")

  val sqlshellSettings: Seq[sbt.Project.Setting[_]] =
  inConfig(SQLShell)(Seq(

    aboutInfo <<= aboutInfoTask
  ))

  private def aboutInfoTask = {
    (classDirectory in Compile, name, version, scalaVersion, streams) map {

      (classes, n, v, sv, streams) =>

      val aboutInfoPath = classes / "org" / "clapper" / "sqlshell" /
                          "SQLShell.properties"
      createAboutInfo(aboutInfoPath, n, v, sv, streams.log)
    }
  }

  private def createAboutInfo(aboutInfoPath: File,
                              projectName: String,
                              projectVersion: String,
                              scalaVersion: String,
                              log: Logger): Unit = {

    val fullPath = aboutInfoPath.absolutePath
    log.info("Creating \"about\" properties in " + fullPath)
    val dir = new File(FileUtil.dirname(fullPath))
    if ((! dir.exists) && (! dir.mkdirs))
      throw new Exception("Can't create directory path: " + dir.getPath)

    val aboutProps = new Properties
    val formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    aboutProps.setProperty("sqlshell.name", projectName)
    aboutProps.setProperty("sqlshell.version", projectVersion)
    aboutProps.setProperty("build.timestamp", formatter.format(new Date))
    aboutProps.setProperty("build.compiler", "Scala " + scalaVersion)

    val system = Map.empty[String, String] ++ SysUtil.systemProperties
    val osName = system.get("os.name")
    val osVersion = system.get("os.version")
    (osName, osVersion) match {
      case (Some(name), None) => 
        aboutProps.setProperty("build.os", name)
      case (Some(name), Some(version)) =>
        aboutProps.setProperty("build.os", name + " " + version)
      case _ =>
        aboutProps.setProperty("build.os", "unknown")
    }

    aboutProps.store(new FileOutputStream(fullPath),
                     "Automatically generated SQLShell build information")
  }

  def copyFiles(sourceFiles: Seq[File], targetDir: File, log: Logger): Unit = {
    for (f <- sourceFiles) {
      val target = targetDir / f.getName
      log.info("Copying \"%s\" to \"%s\"" format (f, target))
      IO.copyFile(f, target)
    }
  }
}
