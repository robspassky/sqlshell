How to Build SQLShell
=====================

Introduction
------------

SQLShell is a SQL command line tool, similar in concept to tools like
Oracle's [SQL*Plus] [sqlplus], the PostgreSQL `psql` command, and
MySQL's `mysql` tool.

[sqlplus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html

Building SQLShell
-----------------

Building SQLShell requires [SBT] [sbt] (the Simple Build Tool), version
0.7.0 or better. Install SBT 0.7.0, as described in the [SBT wiki] [sbt-setup].
Then, run

    sbt update

to pull down the external dependencies. After that step, build SQLShell with:

    sbt compile test package

The resulting jar file will be in the top-level `target` directory.

To build the installer, you currently need to have the [IzPack][izpack]
product installed, and you need to set `IZPACK_HOME` set to its top-level
directory. Once that's in place, you can build the installer with

    sbt installer

[izpack]: http://izpack.org/
[sbt]: http://code.google.com/p/simple-build-tool
[sbt-setup]: http://code.google.com/p/simple-build-tool/wiki/Setup

---
Copyright &copy; 2009 Brian M. Clapper, <i>bmc@clapper.org</i>

