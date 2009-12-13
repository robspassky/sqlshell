#!/bin/sh
#
# Front-end Unix shell script for the SQLShell tool.
#
# $Id$
# ---------------------------------------------------------------------------
# This software is released under a BSD-style license:
#
# Copyright (c) 2009 Brian M. Clapper. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
#
# 2. The end-user documentation included with the redistribution, if any,
#    must include the following acknowlegement:
#
#       "This product includes software developed by Brian M. Clapper
#       (bmc@clapper.org, http://www.clapper.org/bmc/). That software is
#       copyright (c) 2009 Brian M. Clapper."
#
#    Alternately, this acknowlegement may appear in the software itself,
#    if wherever such third-party acknowlegements normally appear.
#
# 3. Neither the names "clapper.org", "SQLShell" nor any of the names of
#    the project contributors may be used to endorse or promote products
#    derived from this software without prior written permission. For
#    written permission, please contact bmc@clapper.org.
#
# 4. Products derived from this software may not be called "SQLSHell" nor
#    may "clapper.org" appear in their names without prior written permission
#    of Brian M. Clapper.
#
# THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
# WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
# NO EVENT SHALL BRIAN M. CLAPPER BE LIABLE FOR ANY DIRECT, INDIRECT,
# INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
# THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
# ---------------------------------------------------------------------------

vm_opts=
while [ $# -gt 0 ]
do
    case "$1" in
        -D*|-X*)
            vm_opts="$vm_opts $1"
	    shift
	    ;;
        *)
	    break
	    ;;
    esac
done

if [ "$SQLSHELL_SCALA_OPTS" != "" ]
then
    vm_opts="$vm_opts $SQLSHELL_SCALA_OPTS"
fi

_CP=
_sep=
for i in $INSTALL_PATH/lib/*
do
    _CP="${_CP}${_sep}${i}"
    _sep=":"
done

if [ -n "$CLASSPATH" ]
then
    _CP="$_CP:$_sep:$CLASSPATH"
fi

exec java -cp "$_CP" $vm_opts org.clapper.sqlshell.tool.Tool "${@}"
