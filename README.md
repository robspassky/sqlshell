SQLShell: A SQL Command Shell
=============================

Introduction
------------

This is SQLShell, a SQL command shell that uses JDBC.

Building
--------

Building SQLShell requires [SBT] [sbt] (the Simple Build Tool). Install
SBT, as described at the SBT web site. Then, run

    sbt update

to pull down the external dependencies. After that step, build SQLShell
with:

    sbt update compile test package

The resulting jar file will be in the top-level `target` directory.

  [sbt]: http://code.google.com/p/simple-build-tool
