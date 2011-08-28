package sqlshell.sbt

import grizzled.file.{util => FileUtil}
import grizzled.{sys => SysUtil}
import java.io.{File, FileOutputStream}
import java.util.{Date, Properties}
import java.text.SimpleDateFormat
import sbt._

object build
{
    def createAboutInfo(aboutInfoPath: File,
                        projectName: String,
                        projectVersion: String,
                        scalaVersion: String,
                        log: Logger): Unit =
    {
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
        (osName, osVersion) match
        {
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

    def copyFiles(sourceFiles: Seq[File], targetDir: File, log: Logger): Unit =
    {
        for (f <- sourceFiles)
        {
            val target = targetDir / f.getName
            log.info("Copying \"%s\" to \"%s\"" format (f, target))
            IO.copyFile(f, target)
        }
    }
}
