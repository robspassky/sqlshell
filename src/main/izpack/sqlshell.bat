@echo off
if "%OS%" == "Windows_NT" @setlocal
rem ---------------------------------------------------------------------------
rem Front end Windows script for SQLShell
rem
rem $Id$
rem ---------------------------------------------------------------------------
rem This software is released under a BSD-style license:
rem
rem Copyright (c) 2009 Brian M. Clapper. All rights reserved.
rem
rem Redistribution and use in source and binary forms, with or without
rem modification, are permitted provided that the following conditions are
rem met:
rem
rem 1.  Redistributions of source code must retain the above copyright notice,
rem     this list of conditions and the following disclaimer.
rem
rem 2.  The end-user documentation included with the redistribution, if any,
rem     must include the following acknowlegement:
rem
rem       "This product includes software developed by Brian M. Clapper
rem       (bmc@clapper.org, http://www.clapper.org/bmc/). That software is
rem       copyright (c) 2009 Brian M. Clapper."
rem
rem     Alternately, this acknowlegement may appear in the software itself,
rem     if wherever such third-party acknowlegements normally appear.
rem
rem 3.  Neither the names "clapper.org", "SQLShell", nor any of the names of
rem     the project contributors may be used to endorse or promote products
rem     derived from this software without prior written permission. For
rem     written permission, please contact bmc@clapper.org.
rem
rem 4.  Products derived from this software may not be called "SQLShell",
rem     nor may "clapper.org" appear in their names without prior written
rem     permission of Brian M. Clapper.
rem
rem THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
rem WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
rem MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
rem NO EVENT SHALL BRIAN M. CLAPPER BE LIABLE FOR ANY DIRECT, INDIRECT,
rem INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
rem NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
rem DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
rem THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
rem (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
rem THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
rem ---------------------------------------------------------------------------

set SQLSHELL_SCALA_OPTS=

rem Make sure Java user.home property accurately reflects home directory
if NOT "%HOME%"=="" set SQLSHELL_SCALA_OPTS=%SQLSHELL_SCALA_OPTS% -Duser.home="%HOME%"

set _TOOL_CLASSPATH=
if "%_TOOL_CLASSPATH%"=="" (
  for %%f in ("$INSTALL_PATH\lib\*") do call :add_cpath "%%f"
  if "%OS%"=="Windows_NT" (
    for /d %%f in ("$INSTALL_PATH\lib\*") do call :add_cpath "%%f"
  )
)

if NOT "%CLASSPATH%" == "" call :add_cpath "%CLASSPATH%"

if "%SCALA_HOME%" == "" (
    @echo "SCALA_HOME is not set"
    goto end
)

"%SCALA_HOME%\bin\scala" -cp "%_TOOL_CLASSPATH%" %SQLSHELL_SCALA_OPTS% org.clapper.sqlshell.tool.Tool %1 %2 %3 %4 %5 %6 %7 %8 %9
goto end

rem ##########################################################################
rem # subroutines

:add_cpath
  if "%_TOOL_CLASSPATH%"=="" (
    set _TOOL_CLASSPATH=%~1
  ) else (
    set _TOOL_CLASSPATH=%_TOOL_CLASSPATH%;%~1
  )
goto :eof

:end
if "%OS%"=="Windows_NT" @endlocal


