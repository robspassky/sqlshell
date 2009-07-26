SQLShell: A Cross-platform, Cross-database SQL Command Line Tool
================================================================

# User's Guide

This is the SQLShell User's Guide.

# Introduction

SQLShell is a SQL command line tool, similar in concept to tools like
Oracle's [SQL*Plus][sqlplus], the PostgreSQL `psql` command, and
MySQL's `mysql` tool.

  [sqlplus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html

SQLShell is a [Scala][1] rewrite of my Python *sqlcmd* tool (rewritten because,
as it turns out, I think JDBC is more consistent and portable than Python's
DB API).

## Some features at a glance

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

In short, SQLShell is a SQL command tool that attempts to provide the same
interface for all supported databases and across all platforms.

# Prerequisites

SQLShell requires the following:

- [Scala][1], version 2.7.5
- An installed Java runtime, version 1.5 or better.
- Appropriate JDBC drivers for the database(s) you want to use.

# Usage

SQLShell is invoked from the command line. You specify the database either
via a logical name that refers to an entry in your configuration file, or
by passing explicit parameters on the command line. The configuration file
allows you to record the connection information for multiple databases,
then specify a single database via a the least number of unique characters
necessary to find it in the configuration file.

## Command Line

> sqlshell [OPTIONS] *db*

or

> sqlshell [OPTIONS] *driver* *url* \[*user* \[*pw*\]\]

### Options

    -?, -h, --help                      Show this usage message

    -c, --config <config_file>          Specify configuration file. Defaults
                                        to: $HOME/.sqlshell/config

    -n, --no-ansi, --noansi             Disable the use of ANSI terminal
                                        sequences. This option just sets the
                                        initial value for this setting. The
                                        value can be changed later from
                                        withing SQLShell itself.

    -r, --readline <lib_name>           Specify readline libraries to use.

                                        Legal values: editline, getline,
                                        gnu, jline, simple. 

                                        May be specified more than once.
    -s, --stack                         Show all exception stack traces.

    -v, --version                       Show version and exit


### Parameters

* The *db* parameter identifies an alias for the database in the
  configuration file. The configuration section for the specified database
  is assumed to contain all the parameters necessary to connect to the database.

* The *driver* parameter specifies either a fully-qualified JDBC driver
  class name, or an alias defined in the `drivers` section of the
  configuration file. When *driver* is specified, *url* is required.

* The *url* parameter is the JDBC URL of the database to which to connect.
  It is only used when *driver* is specified.

* The *user* and *password* parameters are optional and are necessary for
  certain kinds of databases. *user* and *password* are only used when the
  *driver* and *url* parameters are used.

### Specifying a Database

When specifying the *driver* and *url* (and, optionally, *user* and
*password*) on the command line, you can abbreviate the JDBC driver class,
provided the `drivers` section of your configuration file contains an alias
for the driver. For example, suppose your configuration file's `drivers`
section looks like this:

    [drivers]
    # Driver aliases.
    postgresql = org.postgresql.Driver
    postgres = org.postgresql.Driver
    mysql = com.mysql.jdbc.Driver
    sqlite = org.sqlite.JDBC
    sqlite3 = org.sqlite.JDBC
    oracle = oracle.jdbc.driver.OracleDriver
    access = sun.jdbc.odbc.JdbcOdbcDriver

With those aliases in place, you can connect to a SQLite3 database named
"test.db" in one of the following ways:

    sqlshell org.sqlite.JDBC jdbc:sqlite:test.db
    sqlshell sqlite jdbc:sqlite:test.db

#### Examples:

Connect to a SQLite3 database residing in file `/tmp/test.db`:

    sqlshell org.sqlite.JDBC jdbc:sqlite:/tmp/test.db

Connect to an Oracle database named "customers" on host `db.example.com`,
using user "scott" and password "tiger":

    sqlcmd oracle jdbc:oracle:thin:@db.example.com:1521:customers scott tiger

Connect to a PostgreSQL database named "mydb" on the current host, using user
"psql" and password "foo.bar"::

    sqlcmd postgres jdbc:postgresql://localhost/mydb psql foo.bar

# Configuration File

Specifying the database connection parameters on the command line is both
tedious and error prone, even with a good shell history mechanism. So,
SQLShell permits you to store your database connection information in a
configuration file.

## A Brief Overview of the Configuration File

Things will be a little clearer if we look at a sample configuration file.
The following file specifies the same databases as in the examples, above:

    # sqlshell initialization file

    [settings]
    showbinary: 20

    [db.testdb]
    aliases: sqlite, test
    url: jdbc:sqlite:/tmp/test.db
    driver: sqlite
    history: ${env.HOME}/.sqlshell/test.hist

    [db.customers]
    aliases: oracle
    url: jdbc:oracle:thin:@db.example.com:1521:customers scott tiger
    driver: oracle
    user: scott
    password: tiger

    [db.mydb]
    aliases=postgres
    url=jdbc:postgresql://localhost/mydb
    driver=postgresql
    user=psql
    password=foo.bar

Now, if you store that file in `$HOME/.sqlshell/config` (the default place
SQLShell searches for it), connecting to each of the databases is much simpler:

    $ sqlshell testdb
    $ sqlshell customers
    $ sqlshell mydb

You can store the file somewhere else, of course; you just have to tell
SQLShell where it is:

    $ sqlshell -c /usr/local/etc/sqlshell.cfg testdb
    $ sqlshell -c /usr/local/etc/sqlshell.cfg customers
    $ sqlshell -c /usr/local/etc/sqlshell.cfg mydb

See the next section for details on the specific sections and options in the
SQLShell configuration file.

## Configuration File in Depth

A SQLShell configuration file, typically stored in `$HOME/.sqlshell/config`,
is an INI-style file divided into logical sections. Each of those sections
is described below. All section names must be unique within the file.

Blank lines and comment lines are ignored; comment lines start with a "#"
character.
