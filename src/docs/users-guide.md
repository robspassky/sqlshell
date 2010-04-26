---
title: SQLShell, a cross-platform, cross-database SQL command tool
layout: users-guide
---

## User's Guide

This is the SQLShell User's Guide.

## Introduction

SQLShell is a SQL command line tool, similar in concept to tools like
Oracle's [SQL Plus][sqlplus], the [PostgreSQL][postgresql] `psql` command,
and the [MySQL][mysql] `mysql` tool.

[sqlplus]: http://www.oracle.com/technology/docs/tech/sql_plus/index.html
[postgresql]: http://www.postgresql.org/
[mysql]: http://www.mysql.com/

SQLShell is a [Scala][scala] rewrite of my Python *sqlcmd* tool (rewritten
because, as it turns out, I think JDBC is more consistent and portable than
Python's DB API).

### Some features at a glance

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
* SQLShell supports any database engine for which a JDBC driver exists.
* SQLShell is written in [Scala][scala] and uses some third-party, open-source
  Scala and Java libraries.
* SQLShell is open source, and is licensed under a liberal BSD license.

[scala]: http://www.scala-lang.org/

In short, SQLShell is a SQL command tool that attempts to provide the same
interface for all supported databases and across all platforms.

## Prerequisites

SQLShell requires the following:

- An installed Java runtime, version 1.5 or better.
- Appropriate JDBC drivers for the database(s) you want to use.

SQLShell comes bundled with an appropriate version of the [Scala][scala]
runtime, so you do *not* need to have Scala installed to use SQLShell.

## Usage

SQLShell is invoked from the command line. You specify the database either
via a logical name that refers to an entry in your configuration file, or
by passing explicit parameters on the command line. The configuration file
allows you to record the connection information for multiple databases,
then specify a single database via a the least number of unique characters
necessary to find it in the configuration file.

### Command Line

> sqlshell \[OPTIONS\] *db* \[*@file*\]

or

> sqlshell \[OPTIONS\] *driver* *url* \[*user* \[*pw*\]\] \[*@file*\]

#### Options

    -?, -h, --help                Show this usage message.

    -c, --config <config_file>    Specify configuration file. Defaults to
                                  $HOME/.sqlshell/config

    -n, --no-ansi, --noansi       Disable the use of ANSI terminal sequences.
                                  This option just sets the initial value for
                                  this setting. The value can be changed later
                                  from within SQLShell itself.

    -r, --readline <lib_name>     Specify readline libraries to try to use.
                                  Legal values: editline, getline, gnu, jline, 
                                                simple
                                  May be specified more than once.

    -s, --stack                   Show all exception stack traces.

    -v, --verbose                 Enable various verbose messages. This
                                  option just sets the initial value for this
                                  setting. The value can be changed later from
                                  within SQLShell itself.

    -V, --version                 Show version and exit.

#### Parameters

* The *db* parameter identifies an alias for the database in the
  configuration file. The configuration section for the specified database
  is assumed to contain all the parameters necessary to connect to the database.

* The optional *@file* parameter specifies the path to a file containing
  SQL and SQLShell commands to be executed. If this parameter is specified,
  SQLShell executes the commands in the file, then exits.

* The *driver* parameter specifies either a fully-qualified JDBC driver
  class name, or an alias defined in the `drivers` section of the
  configuration file. When *driver* is specified, *url* is required.

* The *url* parameter is the JDBC URL of the database to which to connect.
  It is only used when *driver* is specified.

* The *user* and *password* parameters are optional and are necessary for
  certain kinds of databases. *user* and *password* are only used when the
  *driver* and *url* parameters are used.

#### Specifying a Database

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
"test.db" using one of the following commands:

    $ sqlshell org.sqlite.JDBC jdbc:sqlite:test.db
    $ sqlshell sqlite jdbc:sqlite:test.db

##### Examples

Connect to a SQLite3 database residing in file `/tmp/test.db`:

    $ sqlshell org.sqlite.JDBC jdbc:sqlite:/tmp/test.db

Connect to an Oracle database named "customers" on host `db.example.com`,
using user "scott" and password "tiger":

    $ sqlshell oracle jdbc:oracle:thin:@db.example.com:1521:customers scott tiger

Connect to a PostgreSQL database named "mydb" on the current host, using user
"psql" and password "foo.bar"::

    $ sqlshell postgres jdbc:postgresql://localhost/mydb psql foo.bar

## Configuration File

Specifying the database connection parameters on the command line is both
tedious and error prone, even with a good shell history mechanism. So,
SQLShell permits you to store your database connection information in a
configuration file.

### A Brief Overview of the Configuration File

Things will be a little clearer if we look at a sample configuration file.
The following file specifies the same databases as in the examples, above:

    # sqlshell initialization file

    [settings]
    maxbinary: 20

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

If you store that file in `$HOME/.sqlshell/config` (the default place
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

### Configuration File in Depth

A SQLShell configuration file, typically stored in `$HOME/.sqlshell/config`,
is an INI-style file divided into logical sections. Each of those sections
is described below. All section names must be unique within the file.

Blank lines and comment lines are ignored; comment lines start with a "#"
character.

SQLShell uses the [Grizzled Scala][] library's `grizzled.config` module to
parse its configuration, so it supports all the capabilities that module
provides. The configuration file is a Win.INI-style file, with several
enhancements, and supports:

* Sections, like traditional Windows INI files
* Include directives, so you can include other files within the configuration
* Variable substitutions, allowing you to put common definitions in one section,
  to be used throughout other sections.
* Special `env` and `system` pseudo-sections. See `Variable Substitution`,
  below.
* Java-style metacharacters like `\t`, `\n` and `\u00a9`.
* Comment lines, starting with a "#" character

[Grizzled Scala]: http://bmc.github.com/grizzled-scala/

Each section consists of a set of variable/value pairs. Variable names can
consist of alphanumerics and underscores; values can contain anything. SQLShell
strips leading and trailing white space from the values.

Variables and values are separated either by "=" or ":". For example, the 
following settings are identical:

    foo: bar
    foo=bar
    foo:bar
    foo = bar

There's also a special "raw" assignment operator, `->` that suppresses variable
and metacharacter expansion. Thus, to assign the literal value of "$bar" to
variable `foo`, use

    foo -> $bar

not

    foo = $bar

SQLShell looks for several special sections, based on their names or prefixes.
Other sections are permitted, but SQLShell doesn't explicitly use them. You
can use other sections for common variable definitions; a section called
"common" or "vars" is often useful for that.

#### Including other configuration files

A special `include` directive permits inline inclusion of another
configuration file. The include directive takes two forms:

     %include "path"
     %include "URL"

For example:

     %include "common.cfg"
     %include "/etc/sqlshell/common.cfg"
     %include "http://configs.example.com/mytools/common.cfg"

If the include path is not a URL, and is not an absolute path, its location 
is relative to the file that's trying to include it.

The included file may contain any content that is valid for this parser. It
may contain just variable definitions (i.e., the contents of a section,
without the section header), or it may contain a complete configuration
file, with individual sections. Since SQLShell recognizes a variable
syntax that is essentially identical to Java's properties file syntax, it's
also legal to include a properties file, provided it's included within a
valid section.

#### Variable Substitution

A variable value can interpolate the values of other variables, using a
variable substitution syntax. The general form of a variable reference is
`${sectionName.varName}`. In many cases, the braces can be omitted (e.g.,
`$sectionName.varName`.

* `sectionName` is the name of the section containing the variable to
  substitute; if omitted, it defaults to the current section.
* `varName` is the name of the variable to substitute. 

If a variable reference specifies a section name, the referenced section
must precede the current section. It is not possible to substitute the
value of a variable in a section that occurs later in the file.

SQLShell's configuration supports two special pseudo-sections. These
sections don't really exist, but they can be referenced in variable
substitutions.

`env`

> The `env` pseudo-section contains all the environment variables available
> to SQLShell. For example, on a Unix-like system, you can refer to
> `${env.HOME}` (or `$env.HOME`) to get the home directory of the user
> who's running SQLShell. On some versions of Windows, `${env.USERNAME}`
> will substitute the name of the user running SQLShell. Note: On UNIX
> systems, environment variable names are typically case-sensitive; for
> instance, `${env.USER}` and `${env.user}` refer to different environment
> variables. On Windows systems, environment variable names are typically
> case-insensitive, so `${env.USERNAME}` and `${env.username}` are
> equivalent.

`system`

> The `system` pseudo-section contains all the Java and Scala properties.
> For example, `${system.user.name}` (or `$system.user.name`) gets the
> Java property corresponding to the user's name.

Notes and caveats:

* Variable substitutions are only permitted within variable values. They
  are ignored in variable names, section names, include directives and
  comments.
* Variable substitution is performed after metacharacter expansion (so
  don't include metacharacter sequences in your variable names).
* To include a literal "$" character in a variable value, escape it with a
  backslash, e.g., "var=value with \$ dollar sign", or use the `->` assignment
  operator.

#### The *settings* Section

The optional `settings` section can contain initial values for any of the
settings that are understood by the `.set` command. See the description of
`.set`, below, for a full explanation of each setting. Here's an example:

    [settings]
    # Show up to 20 characters of CLOB and BLOB data
    maxbinary: 20

#### The *drivers* section

The optional `drivers` section contains alias names for JDBC drivers. Using
this section, you can assign short names to the JDBC driver class names,
allowing you to refer to the short names--both in the configuration file and
on the command line--rather than the longer Java class names.

For example:

    [drivers]
    # Driver aliases.
    postgresql = org.postgresql.Driver
    postgres = org.postgresql.Driver
    mysql = com.mysql.jdbc.Driver
    sqlite = org.sqlite.JDBC
    sqlite3 = org.sqlite.JDBC
    oracle = oracle.jdbc.driver.OracleDriver
    access = sun.jdbc.odbc.JdbcOdbcDriver

#### The *db_* Sections

A `db_` section contains the connection definition for a particular
database. The `db_` prefix must be followed by the primary name of the
database. Multiple `db_` sections can exist in the configuration file; each
section supports the following parameters.

`aliases`

> A white space- or comma-delimited list of alternate names for the database.
> These are other names by which you can identify the database to SQLShell,
> on the command line. For example:

    [db_employees]
    aliases: payroll, people

> With that set of alias definitions, you could connect to the database using
> any of the following command lines (among others), provided that the alias
> names don't conflict with those of other databases in the configuration file:

    $ sqlshell employees
    $ sqlshell payroll
    $ sqlshell people
    $ sqlshell emp

`driver`

> Either the JDBC driver class to use to connect to the database, or the name
> of a driver alias specified in the `[drivers]` section. For example:

    [db_employees]
    aliases: payroll, people
    driver: org.postgresql.Driver

> or

    [drivers]
    postgres = org.postgresql.Driver

    [db_employees]
    aliases: payroll, people
    driver: postgres

`url`

> The JDBC URL used to connect to the database. For example:

    [db_employees]
    aliases: payroll, people
    driver: postgres
    url: jdbc:postgresql://db.example.com/empdb

`schema`

> The schema to use when resolving table names. If omitted, then SQLShell
> considers all tables that are visible to the connected user. This
> variable just sets the initial value for this setting. The value can be
> changed later from within SQLShell itself.

`user`

> The user name to use when authenticating to the database. Some database
> types, such as SQLite, don't require a user or password.

`password`

> The password to use when authenticating to the database. Some database
> types, such as SQLite, don't require a user or password.

`history`

> The full path to the history file for this database. If no history file
> is specified, then SQLShell will not save the command history for this
> database. If you *do* specify a history file for the database, then SQLShell
> saves the command history (that is, what you've typed in SQLShell), up to
> `maxhistory` commands, so you can use them the next time to connect to this
> database. `maxhistory` is a SQLShell setting, described below.
>
> For example:

    [common]
    historyDir: ${env.HOME}/.sqlshell

    [drivers]
    postgres = org.postgresql.Driver

    [db_employees]
    aliases: payroll, people
    driver: postgres
    url: jdbc:postgresql://db.example.com/empdb
    user: admin
    password: foobar1
    history: $common.historyDir/employees.hist

### A Note about Database Names

When you specify the name of a database on the SQLShell command line,
SQLShell attempts to match that name against the names of all databases in
the configuration file. SQLShell compares the name you specify against the
following values from each `db_` configuration section:

* The section name, minus the `db_` prefix. This is the primary name of
  the database, from SQLShell's perspective.
* Any names in the `aliases` variable within that section (if there is
  an `aliases` variable).

You only need to specify as many characters as are necessary to uniquely
identify the database.

Thus, given this configuration file:

    [common]
    historyDir: ${env.HOME}/.sqlshell

    [drivers]
    postgres = org.postgresql.Driver

    [db_employees]
    aliases: payroll, people
    driver: postgres
    url: jdbc:postgresql://db.example.com/empdb
    user: admin
    password: foobar1
    history: $common.historyDir/employees.hist

You can connect to the `employees` database using any of the following
names:

- `employees`: the section name, minus the "db_" prefix.
- `payroll`: one of the aliases
- `people`: the other alias
- `emp`: a unique abbreviation of `employees`, the database name
- `pay`: a unique abbreviation of `payroll`, a database alias


## Using SQLShell

When invoked, SQLShell prompts on standard input with "sqlshell>" and waits
for commands to be entered, executing each one as it's entered. It
continues to prompt for commands until either:

- it encounters an end-of-file condition (Ctrl-D on Unix systems, Ctrl-Z
  on Windows), or
- you type the `exit` command.

Some commands (e.g., all SQL commands, and some others) are not executed until
a final ";" character is seen on the input; this permits multi-line commands.
Other commands, such as internal commands like `.set`, are single-line
commands and do not require a semi-colon.

Before going into each specific type of command, here's a brief SQLShell
transcript, to whet your appetite:

    $ sqlshell mydb
    SQLShell, version 0.4 (2010/02/20 16:13:25)
    Using Java EditLine
    Type "help" for help. Type ".about" for more information.

    sqlshell> .set
             ansi: true
          catalog: 
             echo: false
          logging: info
        maxbinary: 20
   maxcompletions: 30
       maxhistory: 2147483647
           prompt: sqlshell>
           schema: public
      showresults: true
     showrowcount: true
      showtimings: true
     sortcolnames: false
       stacktrace: false

    sqlshell> .desc database
    Connected to database: jdbc:postgresql://localhost:5432/bmc
    Connected as user:     moe
    Database vendor:       PostgreSQL
    Database version:      8.3.4
    JDBC driver:           PostgreSQL Native Driver
    JDBC driver version:   PostgreSQL 8.2 JDBC3 with SSL (build 505)
    Transaction isolation: read committed
    Open transaction?      no

    sqlshell> .show tables;
    users    customers

    sqlshell> .desc users
    -----------
    Table users
    -----------
    Id                  int4 NOT NULL,
    CompanyId           int4 NOT NULL,
    EmployerAssignedId  varchar(100) NULL,
    LastName            varchar(254) NOT NULL,
    FirstName           varchar(254) NOT NULL,
    MiddleInitial       bpchar(1) NULL,
    AddressId           int4 NULL,
    Email               varchar(254) NOT NULL,
    Telephone           varchar(30) NULL,
    Department          varchar(254) NULL,
    IsEnabled           bpchar(1) NOT NULL,
    CreationDate        timestamp NOT NULL

    sqlshell> select id, companyid, lastname, firstname, middleinitial, employer from users;
    Execution time: 0.1 seconds
    Retreival time: 0.2 seconds
    2 rows returned.

    Id  CompanyId  LastName  FirstName  MiddleInitial  EmployerAssignedId
    --  ---------  --------  ---------  -------------  ------------------
    1   1          Clapper   Brian      M              1                 
    2   1          User      Joe        NULL           1                 

### SQL

SQLShell will issue any valid SQL command. It does not interpret the SQL
command at all, beyond recognizing the initial `SELECT`, `INSERT`,
`UPDATE`, etc., statement. Thus, RDBMS-specific SQL is perfectly permissable.

For SQL commands that produce results, such as `SELECT`, SQLShell displays
the result in a tabular form, using as little horizontal real estate as
possible. It does **not** wrap its output, however.

SQLShell has explicit support for the following kinds of SQL statements.
Note that "explicit support" means SQLShell can do table-name completion
for those commands (see [Command Completion][]), not that SQLShell understands
the SQL syntax.

[Command Completion]: #command_completion

- `ALTER` (e.g., `ALTER TABLE`, `ALTER INDEX`)
- `CREATE` (e.g., `CREATE TABLE`, `CREATE INDEX`)
- `DELETE`
- `DROP` (e.g., `DROP TABLE`, `DROP INDEX`)
- `INSERT`
- `UPDATE`

Within those SQL commands, tab completion completes table names.


### Timings

By default, SQLShell times how long it takes to execute a SQL statement
and prints the resulting times on the screen. To suppress this behavior,
set the `timings` setting to `false`:

    .set showtimings off

### SQL Echo

By default, SQLShell does *not* echo commands to the screen. That's a
reasonable behavior when you're using SQLShell interactively. However, when
you're loading a file full of SQLShell statements, you might want each
statement to be echoed before it is run. To enable command echo, set the
`echo` setting to `true`:

    .set echo on

Example of use:

    .set echo on
    .run /tmp/foo.sql
    .set echo off

### Comments

SQLShell honors two kinds of comments:

* normal SQL comments
* SQLShell comment-commands

#### Normal SQL Comments

SQLShell honors (and ignores) SQL comments, as long as each comment is on a
line by itself. A SQL comment begins with "--".

Example of supported syntax:

    -- This is a SQL comment.
    -- And so is this.

Example of *unsupported* syntax:

    INSERT INTO foo VALUES (1); -- initialize foo

#### SQLShell Comment-commands

SQLShell implements some SQL-related commands as special comments. All
such comments start with `--sqlshell-`.

##### Defining a block of SQL (e.g., to create a stored procedure)

SQLShell is RDBMS-agnostic. It doesn't actually parse the SQL it sends to the
remote database. With normal SQL commands like `SELECT` and `INSERT`, SQLShell
simply looks for the final semicolon (";"), and passes everything up to that
semicolon to the database, allowing the database engine to do the parsing.

This strategy works fine for normal SQL command, which can be treated as a
single line. But what about stored procedure definitions?

These (and anything else requiring a block of SQL, possibly with embedded
semicolons) causes problems for SQLShell, because it can't tell where the
command is supposed to end. To solve this problem the "right" way would
require adding more sophisticated parsing logic to SQLShell--parsing logic
that would necessarily be different for every back-end database engine.

SQLShell takes a simpler approach. It allows you to signify the start and
end of a block of SQL, using two special comment lines:

    --sqlshell-block-begin
    ... SQL ...
    --sqlshell-block-end

When SQLShell sees `--sqlshell-block-begin`, on a line by itself, it looks
ahead, for a matching `--sqlshell-block-end` command (also on a line by
itself). SQLShell then issues everything between those commands as one SQL
statement. For example, consider the following [PostgreSQL][postgresql]
function definition (using the PL/pgSQL language):

    --sqlshell-block-begin
    CREATE OR REPLACE FUNCTION totalfoo() RETURNS integer AS $$
    DECLARE
        q int;
    BEGIN
    SELECT COUNT(*) INTO q FROM foo;
    RETURN q;
    END;
    $$ LANGUAGE plpgsql;
    --sqlshell-block-end

SQLShell will issue the entire function definition as one statement.

Because SQLShell's special "block begin" and "block end" command are implemented
as structured comments, the same script you run through SQLShell will also work
in other database tools.

### Other SQLShell-specific Commands

These internal SQLShell commands are one-line commands that do not require
a trailing semi-colon and cannot be on multiple lines. Most (but not all)
of these commands start with a dot (".") character, to distinguish them
from commands that are processed by the connected database engine.

`.about`

> Displays information about SQLShell.

`begin`

> Start a new transaction. This command is not permitted unless
> `autocommit` is on. `begin` is essentially a no-op: It's ignored in
> autocommit mode, and irrelevant when autocommit mode is off. It's there
> primarily for SQL scripts.
>
> Example of use:

    begin
    update foo set bar = 1;
    commit

> For compatibility with SQL scripts, this command does not begin with a ".".

`.capture`

> Captures the results of queries to a CSV file.
> 
> To turn capture on:

    .capture to /path/to/file  -- captures to specified file
    .capture on                -- captures to a temporary file

> To turn capture off:

    .capture off

> Example:

    .capture to /tmp/results.csv
    SELECT * from foo;
    SELECT * from bar;
    .capture off

> SQLShell opens the file for writing (truncating it, if it already exists).
> Then, SQLShell writes each result set to the file, along with column
> headers, until it sees ".capture off".

`commit`

> Commit the current transaction. Ignored if `autocommit` is enabled. For
> compatibility with SQL scripts, this command does not begin with a ".".

`.desc`

> The `.desc` (for "describe") command takes two forms:

    .desc database

> This form displays some information about the connected database.

    .desc table [full]

> This form describes a table, showing the columns and their types. The
> `table` parameter is the table name to describe. If the optional `full`
> parameter is specified, SQLShell also displays the table's indexes and
> foreign key constraints (if any). For example:

    sqlshell> .desc users
    -----------
    Table users
    -----------
    Id                  int4 NOT NULL,
    CompanyId           int4 NOT NULL,
    EmployerAssignedId  varchar(100) NULL,
    LastName            varchar(254) NOT NULL,
    FirstName           varchar(254) NOT NULL,
    MiddleInitial       bpchar(1) NULL,
    AddressId           int4 NULL,
    Email               varchar(254) NOT NULL,
    Telephone           varchar(30) NULL,
    Department          varchar(254) NULL,
    IsEnabled           bpchar(1) NOT NULL,
    CreationDate        timestamp NOT NULL

    Primary key columns: Id

    names_pkey: Unique index on (id)
    namesfirstix: Non-unique index on (firstname)
    nameslastix: Non-unique index on (lastname)

`.echo`

> Echoes all remaining arguments to standard output. This command is useful
> primarily in scripts.
>
> Example:

    .echo Don't look now, but I'm about to run SELECT

`exit`

> Exit SQLShell. `exit` is equivalent to typing the key sequence
> corresponding to an end-of-file condition (Ctrl-D on Unix systems, Ctrl-Z
> on Windows).

`history`

> The `history` command (also callable as `h`), displays the command
> history. The command has the general form:

    history [-n] [regex]

> *n* specifies the maximum number of commands to show from the history. If
> omitted, all commands (or all matching commands, if *regex* is specified)
> are shown.
>
> *regex* is an optional regular expression that can be used to filter the
> list of commands.
>
> Examples:

    history -30         <-- show the last 30 commands
    history -3 ^create  <-- show the last 3 commands that match "^create"

See **Command History** for a complete explanation of SQLShell's
> command history capabilities.

`r` or \!

> Re-issue a command from the history. General usage:

    r [num|str]
    \![num|str]
    \!\!

> If *num* is present, it is the number of the command to re-run. If *str*
> is specified, the most recent command that *str* (using a substring match)
> is re-run.
>
> For example, consider this history:

    sqlshell> history
    1: .show tables;
    2: select * from foo;
    3: .desc foo;
    4: .desc foobar;

> Here are various `redo` invocations:

    sqlshell> r 1  <--- re-runs command 1, ".show tables"
    sqlshell> !1   <--- re-runs command 1, ".show tables"
    sqlshell> \!s   <--- re-runs the most recent command that starts with "s", which is "select * from foo"
    sqlshell> r    <--- re-runs the last command, ".desc foobar"
    sqlshell> \!\!   <--- also re-runs the last command, ".desc foobar"


`rollback`

> Roll the current transaction back. Ignored if `autocommit` is enabled. For
> compatibility with SQL scripts, this command does not begin with a dot.

`.run`

> Loads an external file of commands (typically SQL) and runs those commands in
> the current session without exiting. After the commands are run, SQLShell
> returns to its interactive prompt. Usage:

    .run path

> A leading ~ character is honored as a substitute for the current user's 
> home directory.
>
> Within the `.run` command, tab completion completes the pathname.

`.set`

> The `.set` command displays or alters internal SQLShell settings. Without
> any parameters, `.set` displays all internal settings and their values:

    sqlshell> .set
             ansi: true
          catalog: 
             echo: false
          logging: info
        maxbinary: 20
   maxcompletions: 30
       maxhistory: 2147483647
           prompt: sqlshell>
           schema: public
      showresults: true
     showrowcount: true
      showtimings: true
     sortcolnames: false
       stacktrace: false

> The initial value for any setting may be placed in the `[settings]` section
> of the configuration file.
>
> To set a variable, use either `.set var=value` or `.set var value`. For
> example, the following two statements are equivalent:

    .set echo on
    .set echo=on

> Boolean variables (such as `ansi` and `echo`) can take any of the following
> boolean strings:

    on, 1, yes, true
    off, 0, no, false

> Values can be quoted, either with paired single or double quotes. Currently,
> the only real use for that feature is to clear string settings. Values like
> `schema` can be cleared by setting them to the empty string, as follows:

    .set schema=""

> The supported settings are:
> 
> * `ansi`: Whether or not to use ANSI terminal escape sequences in output.
>   Currently, SQLShell only uses ANSI sequences to display errors and warnings
>   in color. Sometimes, however, it's useful to disable them. **Default:** on
>
> * `catalog`: The catalog to use when resolving table names. If omitted,
>   then SQLShell considers all catalogs that are visible to the connected
>   user. This  value may also be initialized in the configuration section for 
>   the database. **Default:** none
>
> * `echo`: Whether or not commands are echoed before they are run.
>   **Default**: off
>
> * `logging`: The current log level, which affects the information logged
>   to the screen. Legal values are, in order of verbosity: `error`, `info`, 
>   `verbose`, `warning`, `debug`. **Default:** `info`
>
> * `maxbinary`: How many bytes or characters to display from binary (BLOB,
>   CLOB, LONGVARCHAR, etc.) columns. For non-character columns like BLOBs,
>   the bytes are displayed as two-character hexadecimal strings. If this
>   setting is 0, then SQLShell will not retrieve binary values and will display
>   placeholder strings like `<binary>` and `<clob>`, instead. **Default:** 0
>
> * `maxcompletions`: The maximum number of possible completions to show.
>   This setting comes into play when you press the TAB key to complete a
>   string, and there are multiple possible completions for the string.
>   SQLShell will display up to `maxcompletions` of those possible completions,
>   in a columnar form, on the screen. If this setting is 0, then SQLShell
>   will display all of the possible completions.  **Default:** 30
>
> * `maxhistory`: Sets the maximum number of entries in the command history.
>   **Default:** A really large number (the largest possible signed 32-bit
>   integer, 2147483647).
>
> * `prompt`: Sets the primary prompt, which defaults to "sqlshell> ".
>   You can change the prompt to anything you want. The string you pass to
>   `.set prompt` can contain the following escapes, which are substituted
>   as described. In all cases, if an escape has no value, an empty string
>   is subsituted.
>
>   - `%db%`: Substitute the name of the current database, if known.
>     The name is the section name in the configuration file, minus its
>     "db_" prefix.
>   - `%dbtype%`: The database type (Oracle, Microsoft SQL Server, etc.)
>   - `%user%`: The name of the user connected to the database.
>   - `%SP%`: A space
>
> * `schema`: The schema to use when resolving table names. If omitted,
>   then SQLShell considers all tables that are visible to the connected
>   user. This variable just sets the initial value for this setting. This 
>   value may also be initialized in the configuration section for the database.
>   **Default:** none
>
> * `showrowcount`: Whether or not to display the number of rows retrieved or
>   affected by a SQL statement. **Default:** on
>
> * `showtimings`: Whether or not to display how long each SQL statement takes
>   to run. **Default:** on
>
> * `sortcolnames`: Whether or not to sort the names of the columns when
>   describing a table (via the `.desc` command). If this variable is off,
>   then columns are shown in the order the RDBMS returns them.
>   **Default:**: off
>
> * `stacktrace`: Whether or not to display Scala stack traces when internal
>   exceptions occur. Useful mostly for debugging. **Default:** off

`.show`

> The `.show` command displays certain information about the currently
> connected database. Currently, it supports two sub-commands:

    .show schemas

> Show the names of all user-visible schemas in the database. (There may not
> be any.)

    .show tables [pattern]
    .show tables/schema [pattern]

> Show the list of user-visible tables. If a schema is specified, restrict the
> list to the tables in that schema. If no schema is specified, then either the
> default schema (see `.set schema`) is used, or all visible tables are shown.
>
> `pattern`, if specified, is a full or partial regular expression that can be
> used to restrict the table names that are shown.
>
> For example:

    sqlshell> .show tables
    all_users  foo     fool
    tb_bar     tb_foo  userlocation

    sqlshell> .show tables ^tb
    tb_bar  tb_foo

    sqlshell> .show tables tb
    tb_bar  tb_foo

    sqlshell> .show tables ^.*foo
    foo  fool  tb_foo

    sqlshell> .show tables foo$
    foo  tb_foo

As you can see from the example, the regular expression is implicitly anchored
to the beginning of the table name.

### Extended Commands

If you type a command that SQLShell doesn't recognize as a SQL command or one
of its internal commands, it passes the command straight through to the
database and treats the command as it would treate a SQL `SELECT`. This
policy allows you to use certain RDBMS-specific commands without SQLShell
having to support them explicitly. For instance, here's what happens if you've
connected SQLShell to a SQLite database and you try to use the SQLite
`EXPLAIN` command:

    sqlshell> explain select distinct id from foo;
    41 rows returned.
    Execution time: 0.30 seconds
    
    addr  opcode         p1  p2  p3  p4                 p5  comment
    ----  -------------  --  --  --  -----------------  --  -------
    0     Trace          0   0   0                      00  <null> 
    1     OpenEphemeral  1   2   0   keyinfo(1,BINARY)  00  <null> 
    2     Integer        0   3   0                      00  <null> 
    3     Integer        0   2   0                      00  <null> 
    4     Gosub          5   34  0                      00  <null> 
    5     Goto           0   37  0                      00  <null> 
    6     OpenRead       0   2   0   1                  00  <null> 
    7     Rewind         0   13  0                      00  <null> 
    8     Column         0   0   8                      00  <null> 
    9     Sequence       1   9   0                      00  <null> 
    10    MakeRecord     8   2   10                     00  <null> 
    11    IdxInsert      1   10  0                      00  <null> 
    12    Next           0   8   0                      01  <null> 
    13    Close          0   0   0                      00  <null> 
    14    Sort           1   36  0                      00  <null> 
    15    Column         1   0   7                      00  <null> 
    16    Compare        6   7   1   keyinfo(1,BINARY)  00  <null> 
    17    Jump           18  22  18                     00  <null> 
    18    Move           7   6   1                      00  <null> 
    19    Gosub          4   29  0                      00  <null> 
    20    IfPos          3   36  0                      00  <null> 
    21    Gosub          5   34  0                      00  <null> 
    22    Column         1   0   1                      00  <null> 
    23    Integer        1   2   0                      00  <null> 
    24    Next           1   15  0                      00  <null> 
    25    Gosub          4   29  0                      00  <null> 
    26    Goto           0   36  0                      00  <null> 
    27    Integer        1   3   0                      00  <null> 
    28    Return         4   0   0                      00  <null> 
    29    IfPos          2   31  0                      00  <null> 
    30    Return         4   0   0                      00  <null> 
    31    SCopy          1   11  0                      00  <null> 
    32    ResultRow      11  1   0                      00  <null> 
    33    Return         4   0   0                      00  <null> 
    34    Null           0   1   0                      00  <null> 
    35    Return         5   0   0                      00  <null> 
    36    Halt           0   0   0                      00  <null> 
    37    Transaction    0   0   0                      00  <null> 
    38    VerifyCookie   0   1   0                      00  <null> 
    39    TableLock      0   2   0   foo                00  <null> 
    40    Goto           0   6   0                      00  <null> 

Here's an example of running `ANALYZE` on a PostgreSQL database:

    sqlshell> analyze verbose;
    Execution time: 0.r4 seconds
    0 rows

#### Restrictions

* Some extended commands don't work well through SQLShell. Your mileage
  may vary.
* Since these extended commands are database-specific, they do not show
  up in command completion output, and they do not support command completion
  themselves.

## Command History

SQLShell supports a [`bash`][bash]-like command history mechanism. Every
command you type at the command prompt is saved in an internal memory
buffer, accessible via the `history` command.

[bash]: http://www.gnu.org/software/bash/manual/

SQLShell also supports a variety of readline-like libraries, including
[EditLine][editline], [GNU Readline][gnu-readline] and [JLine][jline].
(JLine is shipped with SQLShell.) This means you can use the usual key
bindings (the arrow keys, Emacs keys, etc.) to scroll through your history
list, edit previous commands, and re-issue them.

[gnu-readline]: http://tiswww.case.edu/php/chet/readline/rltop.html
[editline]: http://www.thrysoee.dk/editline/
[jline]: http://jline.sourceforge.net/

Upon exit, SQLShell saves its internal history buffer to a database-specific
file, as long as the database's configuration section specifies a history file
path via the `history` variable. For example:

    [common]
    historyDir: ${env.HOME}/.sqlshell

    [drivers]
    postgres = org.postgresql.Driver

    [db_employees]
    aliases: payroll, people
    driver: postgres
    url: jdbc:postgresql://db.example.com/empdb
    user: admin
    password: foobar1
    history: $common.historyDir/employees.hist

The history file for the `employees` database is
`$HOME/.sqlshell/employees.hist`.

## Command Completion

SQLShell supports TAB-completion in various places, in the manner of the GNU
`bash` shell. TAB-completion is (mostly) context sensitive. For example:

`.{TAB}`
    Displays a list of all the "." commands

`.set {TAB}`
    Displays the variables you can set.

`.set v{TAB}`
    Completes the variable name that starts with "v". If multiple variables
    start with "v", then the common characters are completed, and a second
    TAB will display all the matching variables.

`.connect {TAB}`
    shows all the database names and aliases in the config file

`.connect a{TAB}`
    Completes the database name or alias that starts with "a". If multiple
    names start with "a", then the common characters are completed, and a second
    TAB will display all the matching names.

`select * from {TAB}`
    Shows the tables in the current database. (So does `select `\ *{TAB}*,
    actually.) This works for `insert`, `update`, `delete`, `drop`,
    and `.desc`, as well. The completion in SQL commands *only* completes
    table names; it is not currently sensitive to SQL syntax.

`.history {TAB}`
    Shows the commands in the history.

`.history s{TAB}`
    Shows the names of all commands in the history beginning with "s".

`.run {TAB}`
    Lists all the files in the current directory

`.run f{TAB}`
    Lists all the files in the current directory that start with "s"

`.run ~/{TAB}`
    Lists all the files in your home directory

`.run ~/d{TAB}`
    Lists all the files in your home directory that start with "d"

etc.

## Readline Implementations

SQLShell supports the following readline libraries:

* [Editline][editline], via the [Java EditLine][javaeditline] JNI library.
  Editline has a more liberal BSD license. However, SQLShell cannot ship
  with built-in Editline support, because the process of enabling Editline
  differs on each platform. See below for instructions on getting Editline
  to work.

  On Unix-like systems, SQLShell works best with EditLine.

* [JLine][jline]. JLine supports some of the "standard" Readline capabilities,
  and it's the readline library used by the [Scala][scala] REPL. It's
  self-contained, requiring the installation of no third-party libraries.
  It also has a license permitting its inclusion and use in other software.
  SQLShell ships with JLine. But JLine doesn't support many of the features
  of more advance readline libraries, such as GNU Readline and Editline. For
  instance, it lacks support for non-arrow key escape sequences and an
  incremental reverse history search.

  On Windows systems, JLine is really your only option (aside from "simple",
  below).

* [GNU Readline][gnu-readline], via the JNI [Java-Readline][javareadline]
  library. GNU Readline is licensed using the GNU Public License (GPL). If
  SQLShell were to link with GNU Readline, it, too, would have to be
  GPL-licensed. In addition, GNU Readline requires the presence of
  platform-specific C libraries. For those reasons, SQLShell does not ship
  with GNU Readline. However, if you install the right pieces, SQLShell
  *will* work with GNU Readline. See below.

  *Note*: The [Java-Readline][javareadline] provides limited completion
  information. If you use GNU Readline with SQLShell, completion will not
  always work as expected.

* "Simple", a simple Java-only library. "Simple" doesn't support keybindings,
  but it does support a command history. If you have the option of using
  JLine, GNU Readline, or Editline, you'll probably never want to use the
  "Simple" library; it's included solely as a fallback.

[javareadline]: http://java-readline.sourceforge.net/
[javaeditline]: http://www.clapper.org/software/java/javaeditline/

By default, SQLShell looks for the following readline libraries, in order:

* Editline
* GNU Readline
* JLine
* Simple

### Getting Editline to work

Editline keyboard bindings aren't always "correct" out of the box. For instance,
tab completion didn't work for me on Ubuntu or Mac OS X. The underlying
[Java EditLine][javaeditline]
problem, I put the following line in my `$HOME/.editrc` file:

    bind \\t ed-complete

Since I am also a long-time Emacs user, I want an incremental reverse-search
capability. To do that with Editline requires an additional line in `.editrc`:

    bind '^R' em-inc-search-prev

Here are instructions on getting Editline installed and working on various
platforms.

#### Mac OS X

* Install the [Java EditLine][javaeditline] library, as described at its
  web site.
* Be sure that `libjavaeditline.jnilib` is in your `LD_LIBRARY_PATH`
  environment variable, and that `javaeditline.jar` is in your `CLASSPATH`.
* See the *editrc*(5) man page for keybinding customization instructions.

#### BSD

* Install the [Java EditLine][javaeditline] library, as described at its
  web site.
* Be sure that `libjavaeditline.jnilib` is in your `LD_LIBRARY_PATH`
  environment variable, and that `javaeditline.jar` is in your `CLASSPATH`.
* See the *editrc*(5) man page for keybinding customization instructions.

#### Linux

These instructions differ, depending on the Linux distribution.

##### Ubuntu

* Via *apt*, install the `libedit2` package, to get the base Editline C
  library and headers.
* Install the [Java EditLine][javaeditline] library, as described at its
  web site.
* Be sure that `libjavaeditline.jnilib` is in your `LD_LIBRARY_PATH`
  environment variable, and that `javaeditline.jar` is in your `CLASSPATH`.
* See the *editrc*(5) man page for keybinding customization instructions.

### Getting GNU Readline to work

#### Mac OS X

For Mac OS X, follow the instructions at
<http://blog.toonetown.com/2006/07/java-readline-on-mac-os-x-update.html>.

Note: While SQLShell will report that it is using GNU Readline, Mac OS X
actually uses the Editline library, with a shim library to make it look more
or less like GNU Readline to calling programs. To customize the key bindings,
see the *editrc*(5) man page.

#### BSD

*To be written*

#### Linux

These instructions differ, depending on the Linux distribution.

##### Ubuntu

* Via *apt*, install the `libreadline-java` package.
* Ensure that `/usr/share/java/libreadline-java.jar` is in your `CLASSPATH`.
* Ensure that `LD_LIBRARY_PATH` contains `/usr/lib/jni`

### Using a Specific Readline Library

You can force SQLShell to try to use a specific readline library, or to
restrict the set of libraries it tries to find, via the `-r` (`--readline`)
command line option. For instance, to force SQLShell to try Editline before
Readline, you can use this command line:

    $ sqlshell -r editline -r gnu -r jline somedbname


## License and Copyright

Licenses
========

---

## SQLShell License

This software is released under a BSD license, adapted from
<http://opensource.org/licenses/bsd-license.php>

Copyright &copy; 2009, 2010 Brian M. Clapper.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name "clapper.org" nor the names of its contributors may be
  used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

---

## Licenses for Software Installed with SQLShell

### Scala

SQLShell ships with the [Scala][scala] runtime and library.
Scala is copyright &copy; 2002-2010 EPFL, Lausanne, unless otherwise
specified. All rights reserved.

Scala, and its libraries, have the following disclaimer:

THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
DAMAGE.

For additional information, see the [Scala license][scala-license], at
<http://www.scala-lang.org/node/146>.

[scala]: http://www.scala-lang.org/
[scala-license]: http://www.scala-lang.org/node/146

### Java-Readline

SQLShell ships with the [Java-Readline][java-readline] jar file. Java-Readline
is licensed under the [GNU Lesser General Public License][LGPL] (LGPL) and is
copyright &copy; 1998-2001 Bernhard Bablok.

[java-readline]: http://java-readline.sourceforge.net/
[LGPL]: http://www.gnu.org/licenses/lgpl.html

### Java-Editline

SQLShell ships with the [Java EditLine][javaeditline] library, a JNI
wrapper around the BSD [Editline][editline] library. Java EditLine is released
under a BSD-style license and is copyright &copy; 2010 Brian M. Clapper.

[javaeditline]: http://www.clapper.org/software/java/javaeditline/
[editline]: http://www.thrysoee.dk/editline/

### JLine

SQLShell will run with the [JLine][jline] library, a cross-platform command
editing library. JLine is released under a [BSD][os-bsd] license under a
BSD-style license and is copyright &copy; 2002-2006, Marc Prud'hommeaux.

[jline]: http://jline.sourceforge.net/
[os-bsd]: http://www.opensource.org/licenses/bsd-license.php

### Grizzled Scala Library

SQLShell ships with the [Grizzled Scala][grizzled-scala] library, which is
licensed under a BSD-style license and is copyright &copy; 2009-2010
Brian M. Clapper.

[grizzled-scala]: http://www.clapper.org/software/scala/grizzled-scala/

### opencsv

SQLShell ships with the [opencsv][opencsv] library, which is licensed under
the [Apache 2.0 license][apache-license] and is copyright &copy; 2005
Bytecode Pty Ltd.

[opencsv]: http://opencsv.sourceforge.net/
[apache-license]: http://www.apache.org/licenses/LICENSE-2.0

### JodaTime

SQLShell ships with the [Joda Time][joda-time] library, which is licensed
under the [Apache 2.0 license][apache-license] and is copyright &copy;
2002-2009 Joda.org.

[joda-time]: http://joda-time.sourceforge.net/index.html

### jopt-simple

SQLShell ships with the [jopt-simple][jopt-simple] library, which is licensed
under the [Apache 2.0 license][apache-license] and is copyright &copy;
2004-2009 Paul R. Holser, Jr.

[jopt-simple]: http://jopt-simple.sourceforge.net/index.html

### IzPack

The SQLShell installer is built using the [IzPack][izpack] installer
generator. IzPack is licensed under the [Apache 2.0
license][apache-license] and is copyright &copy; 2001-2008 Julien Ponge.

[izpack]: http://izpack.org/

---
