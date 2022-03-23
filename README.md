# dominion

The dominion library is a very thin layer of base classes that provide
the basic framework for defining Domain Objects for your application in
Scala.  The main idea behind this library is to encourage the separation
of the declarative definition of your Domain from the more imperative
task of retrieve, mutating, and persisting the objects.  As such, you
will see in the directory structure an `api` section that declares base
abstractions for define `Objects` and `ObjectGroups` and, under the
`libs` section you will see base classes for manipulating those objects
certain different persistent stores.

## Deployment Info

This library is published as a jar file on Sonatype.org. Any time we push to master, the Whipsaw jar is built and released to Sonatype as a snapshot. This is all done with the help of sbt-ci-release. From that site:

git tag pushes are published as regular releases to Maven Central
merge into main commits are published as -SNAPSHOT with a unique version number for every commit
So, in order to push a snapshot you would just push code to the master branch here. To push a new release, you would push a tag like so:

```
git push origin v0.1.6
Newly published jars should be visible on the Sonatype repo via this search.
```

## Testing Info

The Dominion library has some tests that make use of the MongoDB persistence layer. In order to be able to run the tests locally, you would need to have a MongoDB database up and running. Assuming you have docker installed, this line should do the trick:

```
mkdir ~/mongo-data
docker run -d -p 27017:27017 -v ~/mongo-data:/data/db  mongo
```
