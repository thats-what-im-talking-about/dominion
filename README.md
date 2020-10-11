# dominion

The dominion library is a very thin layer of base classes that provide
the basic framework for defining Domain Objects for you application in
Scala.  The main idea behind this library is to encourage the separation
of the declarative definition of your Domain from the more imperative
task of retrieve, mutating, and persisting the objects.  As such, you
will see in the directory structure an `api` section that declares base
abstractions for define `Objects` and `ObjectGroups` and, under the
`libs` section you will see base classes for manipulating those objects
certain different persistent stores.
