package org.clapper.sqlshell

/**
 * Useful place to stash some sorting stuff.
 */
trait Sorter
{
    def nameSorter(a: String, b: String) = a.toLowerCase < b.toLowerCase
}

