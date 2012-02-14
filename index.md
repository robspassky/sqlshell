---
title: SQLShell, a cross-platform, cross-database SQL command tool
layout: withTOC
---

## Introduction

SQLShell is a [Scala][]-based SQL command-line tool, similar in concept to
tools like Oracle's [SQL Plus][], the [PostgreSQL][] *psql* command, and
[MySQL][]'s *mysql* tool.

## Some features at a glance

* Connection parameters for individual databases can be kept in a
  configuration file in your home directory, allowing you to specify a
  short logical name for the database when you connect to it. (Multiple
  logical names are permitted for each database.)
* SQLShell has command history management, with GNU Readline-like support.
  Each database has its own history file.
* SQLShell supports retrieving and displaying database metadata (e.g.,
  getting a list of tables, querying the table's columns and their data
  types, listing the indexes and foreign keys for a table, etc.).
* SQLShell provides a standard interface that looks and behaves the same no
  matter what database you're using.
* SQLShell supports any database engine for which a JDBC driver exists.
* SQLShell is written in Scala and uses some third-party, open-source Scala
  and Java libraries.
* SQLShell is open source, and is licensed under a liberal BSD-style
  license.

In short, SQLShell is a SQL command tool that attempts to provide some
powerful features that are consistent across all supported databases and
platforms.

## Getting SQLShell

### Binary releases

#### Prerequisites

SQLShell requires:

* A [Java 6][] runtime.
* The JDBC drivers for the databases you wish to use.

As of version 0.2, SQLShell comes bundled with an appropriate version of
the Scala runtime, so you do *not* need to have a Scala installation to use
SQLShell.

#### The graphical installer

Install SQLShell via the graphical installer jar, available in the
[downloads area][]:

    java -jar sqlshell-0.8.0-installer.jar

This command will install SQLShell, a front-end Unix shell script or
Windows BAT file, and all the dependencies. The installer jar file is signed
with [my PGP key][].

### Building from source

You can also install SQLShell from source.

#### Prerequisites

Building SQLShell requires [SBT] [sbt] (the Simple Build Tool), version 0.10.

#### Getting the source

Either download the source (as a
zip or tarball) from <http://github.com/bmc/sqlshell/downloads>, or make a
local read-only clone of the [GitHub repository][] using one of the
following commands:

    $ git clone git://github.com/bmc/sqlshell.git
    $ git clone http://github.com/bmc/sqlshell.git

#### Building

Once you have a local `sqlshell` source directory, change your working
directory to the source directory, and type:

Then, run

    sbt update

to pull down the external dependencies. After that step, build SQLShell with:

    sbt compile package

The resulting jar file will be in the top-level `target` directory.

To build the installer, you currently need to have the [IzPack][izpack]
product installed, and you need to set `IZPACK_HOME` set to its top-level
directory. Once that's in place, you can build the installer with

    sbt installer

## Documentation

Consult the [User's Guide][] for complete documentation on SQLShell. The
User's Guide is also shipped with the SQLShell source and can be installed
via the binary graphical installer.

If you're of a mind to do so, you can also peruse the [change log][].

## Copyright and License

SQLShell is copyright &copy; 2009-2010 [Brian M. Clapper][] is released
under a [BSD license][license]. See the accompanying [license][] file.

## Patches

I gladly accept patches from their original authors. Feel free to email
patches to me or to fork the [GitHub repository][] and send me a pull
request. Along with any patch you send:

* Please state that the patch is your original work.
* Please indicate that you license the work to the SQLShell project
  under a [BSD License][license].

[User's Guide]: users-guide.html
[GitHub repository]: http://github.com/bmc/sqlshell
[Scala]: http://www.scala-lang.org/
[SQL Plus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html
[PostgreSQL]: http://www.postgresql.org/
[MySQL]: http://www.mysql.org/
[Java 6]: http://java.sun.com/
[downloads area]: http://github.com/bmc/sqlshell/downloads/
[my PGP key]: http://www.clapper.org/bmc/pgp.html
[izpack]: http://izpack.org/
[sbt]: http://code.google.com/p/simple-build-tool
[sbt-setup]: http://code.google.com/p/simple-build-tool/wiki/Setup
[license]: license.html
[Brian M. Clapper]: mailto:bmc@clapper.org
[change log]: CHANGELOG.html
