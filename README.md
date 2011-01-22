SQLShell: A SQL Command Shell
=============================

Introduction
------------

SQLShell is a SQL command line tool, similar in concept to tools like
Oracle's [SQL*Plus] [sqlplus], the PostgreSQL `psql` command, and
MySQL's `mysql` tool.

SQLShell is a [Scala][] rewrite of my Python [*sqlcmd*][] tool (rewritten
because, as it turns out, JDBC is more consistent and portable than
Python's DB API).

Some Features at a Glance
-------------------------

* Connection parameters for individual databases can be kept in a
  configuration file in your home directory, allowing you to specify a
  short logical name for the database when you connect to it. (Multiple
  logical names are permitted for each database.)

* SQLShell has command history management, with GNU Readline-like
  support. Each database can have its own history file; you can also share
  history files across two or more databases.

* SQLShell supports retrieving and displaying database metadata (e.g.,
  getting a list of tables, querying the table's columns and their
  data types, listing the indexes and foreign keys for a table, etc.).

* SQLShell provides a standard interface that looks and behaves the same,
  no matter what database you're using.

* SQLShell supports any database engine for which a JDBC driver exists.

* SQLShell is written in [Scala][].

* SQLShell is open source, and is licensed under a liberal BSD-style
  license.

[Scala]: http://www.scala-lang.org/
[sqlplus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html
[*sqlcmd*]: https://github.com/bmc/sqlcmd

---
Copyright &copy; 2009-2011 Brian M. Clapper, *bmc@clapper.org*
