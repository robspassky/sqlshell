SQLShell: A SQL Command Shell
=============================

Introduction
------------

SQLShell is a SQL command line tool, similar in concept to tools like
Oracle's [SQL*Plus] [sqlplus], the PostgreSQL `psql` command, and
MySQL's `mysql` tool.

  [sqlplus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html

Building SQLShell
-----------------

Building SQLShell requires [SBT][sbt] (the Simple Build Tool). Install SBT,
as described at the SBT web site. Then, run:

[sbt]: http://code.google.com/p/simple-build-tool

    sbt update

to pull down the external dependencies. After that step, build SQLShell
with:

    sbt compile test package

The resulting jar file will be in the top-level `target` directory.

---
Copyright &copy; 2009 Brian M. Clapper, <i>bmc@clapper.org</i>

