SQLShell: A SQL Command Shell
=============================

Introduction
------------

SQLShell is a SQL command line tool, similar in concept to tools like
Oracle's [SQL*Plus] [sqlplus], the PostgreSQL `psql` command, and
MySQL's `mysql` tool.

  [sqlplus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html

SQLShell is a [Scala][1] rewrite of my Python *sqlcmd* tool (rewritten because,
as it turns out, I think JDBC is more consistent and portable than Python's
DB API).

Some Features at a Glance
-------------------------

* Connection parameters for individual databases can be kept in a
  configuration file in your home directory, allowing you to specify a
  short logical name for the database when you connect to it. (Multiple
  logical names are permitted for each database.)

* SQLShell has command history management, with GNU Readline-like
  support. Each database has its own history file.

* SQLShell supports retrieving and displaying database metadata (e.g.,
  getting a list of tables, querying the table's columns and their
  data types, listing the indexes and foreign keys for a table, etc.).

* SQLShell provides a standard interface that looks and behaves the same
  no matter what database you're using.

* SQLShell supports any database engine or which a JDBC driver exists.

* SQLShell is written in [Scala][1] and uses some third-party, open-source
  Scala and Java libraries.

* SQLShell is open source, and is licensed under a liberal BSD-style
  license.

[1]: http://www.scala-lang.org/

---
Copyright &copy; 2009 Brian M. Clapper, *bmc@clapper.org*
