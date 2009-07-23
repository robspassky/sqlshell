package org.clapper.sqlshell

trait Sorter
{
    def nameSorter(a: String, b: String) = a.toLowerCase < b.toLowerCase
}

