---
title: SQLShell Change Log
layout: default
---

Version 0.7.3:

* Added a signal handler, for interrupts, though it cannot do much, since it
  relies on a Java shutdown hook.
* Now builds with Scala 2.9.0, not 2.8.1.
* Updated to latest release of [sbt-plugins][] library.
* Updated to use [SBT][] 0.7.7.
* Updated to version 1.0.6 of the [Grizzled Scala][] library.
* Updated to version 0.3.1 of the [Argot][] library.

[sbt-plugins]: http://software.clapper.org/sbt-plugins/
[Argot]: http://software.clapper.org/argot/

Version 0.7.2:

* Fixed a bug: In a join, when display columns from two separate tables that
  happen to have the same name, SQLShell was only displaying one of the columns.

* Fixed a SQL Server-specific problem with SQL Server `set` commands, such
  as `set showplan_text on;`, which reported errors, even though they worked.
  Reported by Mark D. Anderson (*mda /at/ discerning.com*).

* Corrected some config-file exception messages (noted by
  *brian.ewins /at/ gmail.com*) by pulling in a new version of the
  [Grizzled Scala][] library.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.7.1:

* Completion handling now tokenizes the input line using white space,
  parentheses and commas as delimiters, not just white space. Required
  updating to [Grizzled Scala][] 1.0.3.
* Updated to [Argot][] 0.2.
* Now compiles against [Scala][] 2.8.1.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[Argot]: http://software.clapper.org/argot/
[Scala]: http://www.scala-lang.org/

Version 0.7:

* Fixed [Issue #5][issue-5]: `.desc` command did not honor `schema` setting.
* Now uses [Argot][] for command-line parsing, instead of [jopt-simple][].
  This change alters the usage output somewhat.
* Updated to [Grizzled Scala][] version 1.0.2
* Now compiles against Scala 2.8.0.

[Argot]: http://software.clapper.org/argot/
[jopt-simple]: http://jopt-simple.sourceforge.net/
[issue-5]: http://github.com/bmc/sqlshell/issues#issue/5
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.6:

* Fixed bug: Assertion failure when you type ".capture off", but you're not
  currently capturing anything.
* Replaced uses of now-deprecated `Math` functions with corresponding functions
  from `scala.math`.
* Updated to [Grizzled Scala][] version 0.7, to pick up changes to
  `grizzled.cmd.CommandInterpreter` that address [Issue #3][issue 3]
  (unknown database name on command line causes SQLShell to leave the terminal
  in a weird state when it exits, when using the EditLine package).
* Updated license to include license terms and copyrights for all third-party
  software shipped with SQLShell.
* Some internal cosmetic code changes.
* Now uses [Markdown plugin][], instead of doing Markdown inline.
* Uses new `org.clapper` [IzPack][] plugin to build installer, instead of
  using an external XML configuration file.
* Removed no-longer-necessary dependency on [IzPack][] standalone compiler.
* Updated usage of `grizzled.config.Configuration` to remove uses of newly
  deprecated methods.
* Corrected SBT build's retrieval of 2.7.7 (old) Grizzled-Scala, needed for
  SBT build file to compile.
* Updated to Scala 2.8.0.RC3.
* Updated to posterous-sbt 0.1.5.
* Updated to [SBT][] version 0.7.4.

[issue 3]: http://github.com/bmc/sqlshell/issues#issue/3
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[IzPack]: http://izpack.org/
[Markdown plugin]: http://software.clapper.org/sbt-plugins/markdown.html

Version 0.5.1:

* Quick update to roll in a fix to the `grizzled.cmd` library.

Version 0.5:

* Fixed a command completion error in the `.show` command.
* Possible completions are now displayed in columns, not one per line.
* SQLShell now supports `--sqlshell-block-begin` and `--sqlshell-block-end`
  structured comments, used to delineate multi-statement SQL that must be
  run all at once, such as stored procedure definitions. See the User's
  Guide for complete details.
* The `showbinary` setting has been renamed to `maxbinary`.
* Added a `maxcompletions` setting, controlling how many possible completions
  are shown when tab-completion results in multiple choices.
* SQLShell now permits setting the primary prompt, via `".set prompt"`.
  The prompt string can contain escapes, such as `"%db%"` (which substitutes
  the current database name) and `"%user%"` (which substitutes the name of
  the connected user). See the User's Guide for details.
* Now uses [SBT][sbt] 0.7.2 to build from source.

[sbt]: http://code.google.com/p/simple-build-tool

Version 0.4.2:

* Fixed a command completion error in the `.show` command.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/

Version 0.4.1:

* Incorporated a new version of the [Grizzled Scala library][grizzled-scala],
  which enhances history handling and fixes some command interpreter bugs.
* Fix: SQLShell was accessing result set metadata after the result set was
  closed. With some databases (e.g., H2), this causes an exception to be
  thrown.
- Factored Markdown and EditFile logic into separate [SBT
  plugins][sbt-plugins], simplifying the build.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/
[sbt-plugins]: http://software.clapper.org/sbt-plugins/

Version 0.4:

* Converted to Scala 2.8.0.
* Now must be compiled with [SBT][sbt] version 0.7.0 or better.
* Added ability to suppress display of `SELECT` result set.
* Added ability to sort the names of the columns in a `.desc TABLE` command,
  depending on the value of the `sortcolnames` setting.
* Got rid of MarkdownJ as the Markdown processor; it's buggy and not maintained
  any more. Markdown is now processed with the Showdown Javascript Markdown
  parser, invoked via Mozilla Rhino.
* Simplified internal `JDBCHelper` class.
* Now bundles `javaeditline.jar` (but not the accompanying JNI library).
  See the [javaeditline web site][javaeditline] for details.

[javaeditline]: http://software.clapper.org/java/javaeditline/
[sbt]: http://code.google.com/p/simple-build-tool

Version 0.3:

* Fixed infinite recursion when receiving results.
* Fixed handling of `--version` and `--help` options.
* Added build time to `--version` output.
* Fixed `OutOfMemoryError` problems on large result sets.
* Changed how retrieval and execution times are displayed, to give slightly
  better real-time feedback.

Version 0.2:

* Enhanced the tab-completion logic so that SQL statements tab-complete
  on table names.
* Now works properly with Microsoft Access tables (though `.show tables`
  doesn't work, with the JDBC-ODBC bridge). With MS Access and the JDBC-ODBC
  bridge, you can't retrieve the same column more than once.
* Installer now bundles `scala-library.jar`, and front-end shell scripts
  now use the Java, not Scala, executable. This change means Scala does not
  have to be installed on the machine for SQLShell to run.

Version 0.1:

* Initial release.

Version 0.7.2:

* Fixed a bug: In a join, when display columns from two separate tables that
  happen to have the same name, SQLShell was only displaying one of the columns.

* Fixed a SQL Server-specific problem with SQL Server `set` commands, such
  as `set showplan_text on;`, which reported errors, even though they worked.
  Reported by Mark D. Anderson (*mda /at/ discerning.com*).

* Corrected some config-file exception messages (noted by
  *brian.ewins /at/ gmail.com*) by pulling in a new version of the
  [Grizzled Scala][] library.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.7.1:

* Completion handling now tokenizes the input line using white space,
  parentheses and commas as delimiters, not just white space. Required
  updating to [Grizzled Scala][] 1.0.3.
* Updated to [Argot][] 0.2.
* Now compiles against [Scala][] 2.8.1.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[Argot]: http://software.clapper.org/argot/
[Scala]: http://www.scala-lang.org/

Version 0.7:

* Fixed [Issue #5][issue-5]: `.desc` command did not honor `schema` setting.
* Now uses [Argot][] for command-line parsing, instead of [jopt-simple][].
  This change alters the usage output somewhat.
* Updated to [Grizzled Scala][] version 1.0.2
* Now compiles against Scala 2.8.0.

[Argot]: http://software.clapper.org/argot/
[jopt-simple]: http://jopt-simple.sourceforge.net/
[issue-5]: http://github.com/bmc/sqlshell/issues#issue/5
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.6:

* Fixed bug: Assertion failure when you type ".capture off", but you're not
  currently capturing anything.
* Replaced uses of now-deprecated `Math` functions with corresponding functions
  from `scala.math`.
* Updated to [Grizzled Scala][] version 0.7, to pick up changes to
  `grizzled.cmd.CommandInterpreter` that address [Issue #3][issue 3]
  (unknown database name on command line causes SQLShell to leave the terminal
  in a weird state when it exits, when using the EditLine package).
* Updated license to include license terms and copyrights for all third-party
  software shipped with SQLShell.
* Some internal cosmetic code changes.
* Now uses [Markdown plugin][], instead of doing Markdown inline.
* Uses new `org.clapper` [IzPack][] plugin to build installer, instead of
  using an external XML configuration file.
* Removed no-longer-necessary dependency on [IzPack][] standalone compiler.
* Updated usage of `grizzled.config.Configuration` to remove uses of newly
  deprecated methods.
* Corrected SBT build's retrieval of 2.7.7 (old) Grizzled-Scala, needed for
  SBT build file to compile.
* Updated to Scala 2.8.0.RC3.
* Updated to posterous-sbt 0.1.5.
* Updated to [SBT][] version 0.7.4.

[issue 3]: http://github.com/bmc/sqlshell/issues#issue/3
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[IzPack]: http://izpack.org/
[Markdown plugin]: http://software.clapper.org/sbt-plugins/markdown.html

Version 0.5.1:

* Quick update to roll in a fix to the `grizzled.cmd` library.

Version 0.5:

* Fixed a command completion error in the `.show` command.
* Possible completions are now displayed in columns, not one per line.
* SQLShell now supports `--sqlshell-block-begin` and `--sqlshell-block-end`
  structured comments, used to delineate multi-statement SQL that must be
  run all at once, such as stored procedure definitions. See the User's
  Guide for complete details.
* The `showbinary` setting has been renamed to `maxbinary`.
* Added a `maxcompletions` setting, controlling how many possible completions
  are shown when tab-completion results in multiple choices.
* SQLShell now permits setting the primary prompt, via `".set prompt"`.
  The prompt string can contain escapes, such as `"%db%"` (which substitutes
  the current database name) and `"%user%"` (which substitutes the name of
  the connected user). See the User's Guide for details.
* Now uses [SBT][sbt] 0.7.2 to build from source.

[sbt]: http://code.google.com/p/simple-build-tool

Version 0.4.2:

* Fixed a command completion error in the `.show` command.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/

Version 0.4.1:

* Incorporated a new version of the [Grizzled Scala library][grizzled-scala],
  which enhances history handling and fixes some command interpreter bugs.
* Fix: SQLShell was accessing result set metadata after the result set was
  closed. With some databases (e.g., H2), this causes an exception to be
  thrown.
- Factored Markdown and EditFile logic into separate [SBT
  plugins][sbt-plugins], simplifying the build.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/
[sbt-plugins]: http://software.clapper.org/sbt-plugins/

Version 0.4:

* Converted to Scala 2.8.0.
* Now must be compiled with [SBT][sbt] version 0.7.0 or better.
* Added ability to suppress display of `SELECT` result set.
* Added ability to sort the names of the columns in a `.desc TABLE` command,
  depending on the value of the `sortcolnames` setting.
* Got rid of MarkdownJ as the Markdown processor; it's buggy and not maintained
  any more. Markdown is now processed with the Showdown Javascript Markdown
  parser, invoked via Mozilla Rhino.
* Simplified internal `JDBCHelper` class.
* Now bundles `javaeditline.jar` (but not the accompanying JNI library).
  See the [javaeditline web site][javaeditline] for details.

[javaeditline]: http://software.clapper.org/java/javaeditline/
[sbt]: http://code.google.com/p/simple-build-tool

Version 0.3:

* Fixed infinite recursion when receiving results.
* Fixed handling of `--version` and `--help` options.
* Added build time to `--version` output.
* Fixed `OutOfMemoryError` problems on large result sets.
* Changed how retrieval and execution times are displayed, to give slightly
  better real-time feedback.

Version 0.2:

* Enhanced the tab-completion logic so that SQL statements tab-complete
  on table names.
* Now works properly with Microsoft Access tables (though `.show tables`
  doesn't work, with the JDBC-ODBC bridge). With MS Access and the JDBC-ODBC
  bridge, you can't retrieve the same column more than once.
* Installer now bundles `scala-library.jar`, and front-end shell scripts
  now use the Java, not Scala, executable. This change means Scala does not
  have to be installed on the machine for SQLShell to run.

Version 0.1:

* Initial release.

Version 0.7.2:

* Fixed a bug: In a join, when display columns from two separate tables that
  happen to have the same name, SQLShell was only displaying one of the columns.

* Fixed a SQL Server-specific problem with SQL Server `set` commands, such
  as `set showplan_text on;`, which reported errors, even though they worked.
  Reported by Mark D. Anderson (*mda /at/ discerning.com*).

* Corrected some config-file exception messages (noted by
  *brian.ewins /at/ gmail.com*) by pulling in a new version of the
  [Grizzled Scala][] library.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.7.1:

* Completion handling now tokenizes the input line using white space,
  parentheses and commas as delimiters, not just white space. Required
  updating to [Grizzled Scala][] 1.0.3.
* Updated to [Argot][] 0.2.
* Now compiles against [Scala][] 2.8.1.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[Argot]: http://software.clapper.org/argot/
[Scala]: http://www.scala-lang.org/

Version 0.7:

* Fixed [Issue #5][issue-5]: `.desc` command did not honor `schema` setting.
* Now uses [Argot][] for command-line parsing, instead of [jopt-simple][].
  This change alters the usage output somewhat.
* Updated to [Grizzled Scala][] version 1.0.2
* Now compiles against Scala 2.8.0.

[Argot]: http://software.clapper.org/argot/
[jopt-simple]: http://jopt-simple.sourceforge.net/
[issue-5]: http://github.com/bmc/sqlshell/issues#issue/5
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.6:

* Fixed bug: Assertion failure when you type ".capture off", but you're not
  currently capturing anything.
* Replaced uses of now-deprecated `Math` functions with corresponding functions
  from `scala.math`.
* Updated to [Grizzled Scala][] version 0.7, to pick up changes to
  `grizzled.cmd.CommandInterpreter` that address [Issue #3][issue 3]
  (unknown database name on command line causes SQLShell to leave the terminal
  in a weird state when it exits, when using the EditLine package).
* Updated license to include license terms and copyrights for all third-party
  software shipped with SQLShell.
* Some internal cosmetic code changes.
* Now uses [Markdown plugin][], instead of doing Markdown inline.
* Uses new `org.clapper` [IzPack][] plugin to build installer, instead of
  using an external XML configuration file.
* Removed no-longer-necessary dependency on [IzPack][] standalone compiler.
* Updated usage of `grizzled.config.Configuration` to remove uses of newly
  deprecated methods.
* Corrected SBT build's retrieval of 2.7.7 (old) Grizzled-Scala, needed for
  SBT build file to compile.
* Updated to Scala 2.8.0.RC3.
* Updated to posterous-sbt 0.1.5.
* Updated to [SBT][] version 0.7.4.

[issue 3]: http://github.com/bmc/sqlshell/issues#issue/3
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[IzPack]: http://izpack.org/
[Markdown plugin]: http://software.clapper.org/sbt-plugins/markdown.html

Version 0.5.1:

* Quick update to roll in a fix to the `grizzled.cmd` library.

Version 0.5:

* Fixed a command completion error in the `.show` command.
* Possible completions are now displayed in columns, not one per line.
* SQLShell now supports `--sqlshell-block-begin` and `--sqlshell-block-end`
  structured comments, used to delineate multi-statement SQL that must be
  run all at once, such as stored procedure definitions. See the User's
  Guide for complete details.
* The `showbinary` setting has been renamed to `maxbinary`.
* Added a `maxcompletions` setting, controlling how many possible completions
  are shown when tab-completion results in multiple choices.
* SQLShell now permits setting the primary prompt, via `".set prompt"`.
  The prompt string can contain escapes, such as `"%db%"` (which substitutes
  the current database name) and `"%user%"` (which substitutes the name of
  the connected user). See the User's Guide for details.
* Now uses [SBT][sbt] 0.7.2 to build from source.

[sbt]: http://code.google.com/p/simple-build-tool

Version 0.4.2:

* Fixed a command completion error in the `.show` command.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/

Version 0.4.1:

* Incorporated a new version of the [Grizzled Scala library][grizzled-scala],
  which enhances history handling and fixes some command interpreter bugs.
* Fix: SQLShell was accessing result set metadata after the result set was
  closed. With some databases (e.g., H2), this causes an exception to be
  thrown.
- Factored Markdown and EditFile logic into separate [SBT
  plugins][sbt-plugins], simplifying the build.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/
[sbt-plugins]: http://software.clapper.org/sbt-plugins/

Version 0.4:

* Converted to Scala 2.8.0.
* Now must be compiled with [SBT][sbt] version 0.7.0 or better.
* Added ability to suppress display of `SELECT` result set.
* Added ability to sort the names of the columns in a `.desc TABLE` command,
  depending on the value of the `sortcolnames` setting.
* Got rid of MarkdownJ as the Markdown processor; it's buggy and not maintained
  any more. Markdown is now processed with the Showdown Javascript Markdown
  parser, invoked via Mozilla Rhino.
* Simplified internal `JDBCHelper` class.
* Now bundles `javaeditline.jar` (but not the accompanying JNI library).
  See the [javaeditline web site][javaeditline] for details.

[javaeditline]: http://software.clapper.org/java/javaeditline/
[sbt]: http://code.google.com/p/simple-build-tool

Version 0.3:

* Fixed infinite recursion when receiving results.
* Fixed handling of `--version` and `--help` options.
* Added build time to `--version` output.
* Fixed `OutOfMemoryError` problems on large result sets.
* Changed how retrieval and execution times are displayed, to give slightly
  better real-time feedback.

Version 0.2:

* Enhanced the tab-completion logic so that SQL statements tab-complete
  on table names.
* Now works properly with Microsoft Access tables (though `.show tables`
  doesn't work, with the JDBC-ODBC bridge). With MS Access and the JDBC-ODBC
  bridge, you can't retrieve the same column more than once.
* Installer now bundles `scala-library.jar`, and front-end shell scripts
  now use the Java, not Scala, executable. This change means Scala does not
  have to be installed on the machine for SQLShell to run.

Version 0.1:

* Initial release.

<<<<<<< HEAD
Version 0.7.3:

* Added a signal handler, for interrupts, though it cannot do much, since it
  relies on a Java shutdown hook.
* Now builds with Scala 2.9.0, not 2.8.1.
* Updated to latest release of [sbt-plugins][] library.
* Updated to use [SBT][] 0.7.7.
* Updated to version 1.0.6 of the [Grizzled Scala][] library.
* Updated to version 0.3.1 of the [Argot][] library.

[sbt-plugins]: http://software.clapper.org/sbt-plugins/
[Argot]: http://software.clapper.org/argot/

=======
>>>>>>> f5ee2a6b5986ddbd2383c8d0b3fac685b039c6b0
Version 0.7.2:

* Fixed a bug: In a join, when display columns from two separate tables that
  happen to have the same name, SQLShell was only displaying one of the columns.

* Fixed a SQL Server-specific problem with SQL Server `set` commands, such
  as `set showplan_text on;`, which reported errors, even though they worked.
  Reported by Mark D. Anderson (*mda /at/ discerning.com*).

<<<<<<< HEAD
* Corrected some config-file exception messages (noted by
  *brian.ewins /at/ gmail.com*) by pulling in a new version of the
=======
* Corrected some config-file exception messages (noted in a personal email,
  by *brian.ewins /at/ gmail.com*) by pulling in a new version of the
>>>>>>> f5ee2a6b5986ddbd2383c8d0b3fac685b039c6b0
  [Grizzled Scala][] library.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.7.1:

* Completion handling now tokenizes the input line using white space,
  parentheses and commas as delimiters, not just white space. Required
  updating to [Grizzled Scala][] 1.0.3.
* Updated to [Argot][] 0.2.
* Now compiles against [Scala][] 2.8.1.

[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[Argot]: http://software.clapper.org/argot/
[Scala]: http://www.scala-lang.org/

Version 0.7:

* Fixed [Issue #5][issue-5]: `.desc` command did not honor `schema` setting.
* Now uses [Argot][] for command-line parsing, instead of [jopt-simple][].
  This change alters the usage output somewhat.
* Updated to [Grizzled Scala][] version 1.0.2
* Now compiles against Scala 2.8.0.

[Argot]: http://software.clapper.org/argot/
[jopt-simple]: http://jopt-simple.sourceforge.net/
[issue-5]: http://github.com/bmc/sqlshell/issues#issue/5
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/

Version 0.6:

* Fixed bug: Assertion failure when you type ".capture off", but you're not
  currently capturing anything.
* Replaced uses of now-deprecated `Math` functions with corresponding functions
  from `scala.math`.
* Updated to [Grizzled Scala][] version 0.7, to pick up changes to
  `grizzled.cmd.CommandInterpreter` that address [Issue #3][issue 3]
  (unknown database name on command line causes SQLShell to leave the terminal
  in a weird state when it exits, when using the EditLine package).
* Updated license to include license terms and copyrights for all third-party
  software shipped with SQLShell.
* Some internal cosmetic code changes.
* Now uses [Markdown plugin][], instead of doing Markdown inline.
* Uses new `org.clapper` [IzPack][] plugin to build installer, instead of
  using an external XML configuration file.
* Removed no-longer-necessary dependency on [IzPack][] standalone compiler.
* Updated usage of `grizzled.config.Configuration` to remove uses of newly
  deprecated methods.
* Corrected SBT build's retrieval of 2.7.7 (old) Grizzled-Scala, needed for
  SBT build file to compile.
* Updated to Scala 2.8.0.RC3.
* Updated to posterous-sbt 0.1.5.
* Updated to [SBT][] version 0.7.4.

[issue 3]: http://github.com/bmc/sqlshell/issues#issue/3
[SBT]: http://code.google.com/p/simple-build-tool
[Grizzled Scala]: http://software.clapper.org/grizzled-scala/
[IzPack]: http://izpack.org/
[Markdown plugin]: http://software.clapper.org/sbt-plugins/markdown.html

Version 0.5.1:

* Quick update to roll in a fix to the `grizzled.cmd` library.

Version 0.5:

* Fixed a command completion error in the `.show` command.
* Possible completions are now displayed in columns, not one per line.
* SQLShell now supports `--sqlshell-block-begin` and `--sqlshell-block-end`
  structured comments, used to delineate multi-statement SQL that must be
  run all at once, such as stored procedure definitions. See the User's
  Guide for complete details.
* The `showbinary` setting has been renamed to `maxbinary`.
* Added a `maxcompletions` setting, controlling how many possible completions
  are shown when tab-completion results in multiple choices.
* SQLShell now permits setting the primary prompt, via `".set prompt"`.
  The prompt string can contain escapes, such as `"%db%"` (which substitutes
  the current database name) and `"%user%"` (which substitutes the name of
  the connected user). See the User's Guide for details.
* Now uses [SBT][sbt] 0.7.2 to build from source.

[sbt]: http://code.google.com/p/simple-build-tool

Version 0.4.2:

* Fixed a command completion error in the `.show` command.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/

Version 0.4.1:

* Incorporated a new version of the [Grizzled Scala library][grizzled-scala],
  which enhances history handling and fixes some command interpreter bugs.
* Fix: SQLShell was accessing result set metadata after the result set was
  closed. With some databases (e.g., H2), this causes an exception to be
  thrown.
- Factored Markdown and EditFile logic into separate [SBT
  plugins][sbt-plugins], simplifying the build.

[grizzled-scala]: http://software.clapper.org/scala/grizzled-scala/
[sbt-plugins]: http://software.clapper.org/sbt-plugins/

Version 0.4:

* Converted to Scala 2.8.0.
* Now must be compiled with [SBT][sbt] version 0.7.0 or better.
* Added ability to suppress display of `SELECT` result set.
* Added ability to sort the names of the columns in a `.desc TABLE` command,
  depending on the value of the `sortcolnames` setting.
* Got rid of MarkdownJ as the Markdown processor; it's buggy and not maintained
  any more. Markdown is now processed with the Showdown Javascript Markdown
  parser, invoked via Mozilla Rhino.
* Simplified internal `JDBCHelper` class.
* Now bundles `javaeditline.jar` (but not the accompanying JNI library).
  See the [javaeditline web site][javaeditline] for details.

[javaeditline]: http://software.clapper.org/java/javaeditline/
[sbt]: http://code.google.com/p/simple-build-tool

Version 0.3:

* Fixed infinite recursion when receiving results.
* Fixed handling of `--version` and `--help` options.
* Added build time to `--version` output.
* Fixed `OutOfMemoryError` problems on large result sets.
* Changed how retrieval and execution times are displayed, to give slightly
  better real-time feedback.

Version 0.2:

* Enhanced the tab-completion logic so that SQL statements tab-complete
  on table names.
* Now works properly with Microsoft Access tables (though `.show tables`
  doesn't work, with the JDBC-ODBC bridge). With MS Access and the JDBC-ODBC
  bridge, you can't retrieve the same column more than once.
* Installer now bundles `scala-library.jar`, and front-end shell scripts
  now use the Java, not Scala, executable. This change means Scala does not
  have to be installed on the machine for SQLShell to run.

Version 0.1:

* Initial release.
