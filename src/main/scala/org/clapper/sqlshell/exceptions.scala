package org.clapper.sqlshell

// Base class for SQLShell exceptions

class SQLShellException(message: String) extends Exception(message)

class SQLShellConfigException(message: String) 
    extends SQLShellException(message)

class CommandLineException(message: String) extends SQLShellException(message)
