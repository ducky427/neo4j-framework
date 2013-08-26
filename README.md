GraphAware Neo4j Framework
==========================

[![Build Status](https://travis-ci.org/graphaware/neo4j-framework.png)](https://travis-ci.org/graphaware/neo4j-framework)

The aim of the GraphAware Framework is to speed-up development with Neo4j and provide useful generic and domain-specific
modules, analytical capabilities, graph algorithm libraries, etc.

The framework can be used in two ways: as a [library of useful tested code](#lib) that simplifies tasks commonly needed when
developing with Neo4j, or as a [real framework with modules](#fw), which perform some (behind-the-scenes) mutations on the graph
as transactions occur. In the latter case, you get the benefit of the "library" as well.

Download
--------

### Releases

Releases are synced to Maven Central repository. To use the latest release, [download it](http://search.maven.org/remotecontent?filepath=com/graphaware/neo4j-framework/1.9-1.2/neo4j-framework-1.9-1.2.jar)
and put it on your classpath. When using Maven, include the following snippet in your pom.xml:

    <dependencies>
        ...
        <dependency>
            <groupId>com.graphaware</groupId>
            <artifactId>neo4j-framework</artifactId>
            <version>1.9-1.4</version>
        </dependency>
        ...
    </dependencies>

### Snapshots

To use the latest development version, just clone this repository, run `mvn clean install` and put the produced .jar
file (found in target) into your classpath. If using Maven for your own development, include the following snippet in
your pom.xml instead of copying the .jar:

    <dependencies>
        ...
        <dependency>
            <groupId>com.graphaware</groupId>
            <artifactId>neo4j-framework</artifactId>
            <version>1.9-1.5-SNAPSHOT</version>
        </dependency>
        ...
    </dependencies>

### Note on Versioning Scheme

The version number has two parts, separated by a dash. The first part indicates compatibility with a Neo4j version.
 The second part is the version of the framework. For example, version 1.9-1.2 is a 1.2 version of the framework
 compatible with Neo4j 1.9.x

<a name="fw"/>
Framework Usage
---------------

Using the framework is very easy. Instantiate it, register desired modules, and start it. For example:

```java
    GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase(); //replace with your own database, probably a permanent one

    GraphAwareFramework framework = new GraphAwareFramework(database);

    framework.registerModule(new SomeModule());
    framework.registerModule(new SomeOtherModule());

    framework.start();

    //use database as usual
```

So far, the only production-ready GraphAware module is the [relationship count module](https://github.com/graphaware/neo4j-relcount).
To write your own module, read the Javadoc on `GraphAwareModule` and `GraphAwareFramework`. It would also be useful to
read the rest of this page to understand the ideas and design decisions behind the framework better.

Framework Usage (Batch)
-----------------------

For populating a database quickly, people sometimes use Neo4j `BatchInserter`s. The framework can be used with those as
well, in the following fashion:

```java
    TransactionSimulatingBatchInserter batchInserter = new TransactionSimulatingBatchInserterImpl(/path/to/your/db);

    GraphAwareFramework framework = new GraphAwareFramework(batchInserter);

    framework.registerModule(new SomeModule());
    framework.registerModule(new SomeOtherModule());

    framework.start();

    //use batchInserter as usual
```

Configuration
-------------

In the above examples, the framework is used with sensible default configuration. At the moment, the only thing that is
configurable on the framework level is the character/String that the framework is using to delimit information in its
internal metadata secretly written into the graph. By default, this separator is the hash character (#). In the unlikely
event of interference with your application logic (e.g. # is used in property keys or values in your application), this
can be changed.

If, for instance, you would like to use the dollar sign ($) as a delimiter instead, instantiate the framework in the
  following fashion:
```java

    GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase();

    FrameworkConfiguration customFrameworkConfig = new BaseFrameworkConfiguration() {
      @Override
      public String separator() {
          return "$";
      }
    };

    GraphAwareFramework framework = new GraphAwareFramework(database, customFrameworkConfig);

    framework.registerModule(new SomeModule());
    framework.registerModule(new SomeOtherModule());

    framework.start();
```

<a name="lib"/>
Features
--------

Whether or not you use the framework as described above, you can always add it as a dependency and take advantage of its
useful features.

<a name="tx"/>
### Simplified Transactional Operations

Every mutating operation in Neo4j must run within the context of a transaction. The code dealing with that typically
involves try-catch blocks and looks something like this:

 ```java
     try {
         //do something useful, can throw a business exception
         tx.success();
     } catch (RuntimeException e) {
         //deal with a business exception
         tx.failure();
     } finally {
         tx.finish(); //can throw a database exception
     }
 ```

GraphAware provides an alternative, callback-based API called `TransactionExecutor` in `com.graphaware.tx.executor`.
`SimpleTransactionExecutor` is a simple implementation thereof and can be used on an instance-per-database basis.
 Since you will typically run a single in-process database instance, you will also only need a single `SimpleTransactionExecutor`.

To create an empty node in a database, you would write something like this.

```java
    GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase(); //only for demo, use your own persistent one!
    TransactionExecutor executor = new SimpleTransactionExecutor(database);

    executor.executeInTransaction(new VoidReturningCallback() {
        @Override
        public void doInTx(GraphDatabaseService database) {
            database.createNode();
        }
    });
```

You have the option of selecting an `ExceptionHandlingStrategy`. By default, if an exception occurs, the transaction will be
 rolled back and the exception re-thrown. This is true for both application/business exceptions (i.e. the exception your
 code throws in the `doInTx` method above), and Neo4j exceptions (e.g. constraint violations). This default strategy is
 called `RethrowException`.

The other available implementation of `ExceptionHandlingStrategy` is `KeepCalmAndCarryOn`. It still rolls back the transaction
in case an exception occurs, but it does not re-throw it (only logs it). To use a different `ExceptionHandlingStrategy`, perhaps
  one that you implement yourself, just pass it in to the `executeInTransaction` method:

```java
  executor.executeInTransaction(transactionCallback, KeepCalmAndCarryOn.getInstance());
```

<a name="batch"/>
### Batch Transactional Operations

It is a common requirement to execute operations in batches. For instance, you might want to populate the database with
data from a CSV file, or just some generated dummy data for testing. If there are many such operations (let's say 10,000
or more), doing it all in one transaction isn't the most memory-efficient approach. On the other hand, a new transaction
for each operation results in too much overhead. For some use-cases, `BatchInserters` provided by Neo4j suffice. However,
operations performed using these do not run in transactions and have some other limitations (such as no node/relationship
 delete capabilities).

GraphAware can help here with `BatchTransactionExecutor`s. There are a few of them:

#### Input-Based Batch Operations

If you have some input, such as lines from a CSV file or a result of a Neo4j traversal, and you want to perform an operation
for each item of such input, use `IterableInputBatchTransactionExecutor`. As the name suggests, the input needs to be in the form
of an `Iterable`. Additionally, you need to define a `UnitOfWork`, which will be executed against the database for each
input item. After a specified number of batch operations have been executed, the current transaction is committed and a
new one started, until we run out of input items to process.

For example, if you were to create a number of nodes from a list of node names, you would do something like this:

```java
    GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase(); //only for demo, use your own persistent one!

    List<String> nodeNames = Arrays.asList("Name1", "Name2", "Name3");  //there will be many more

    int batchSize = 10;
    BatchTransactionExecutor executor = new IterableInputBatchTransactionExecutor<>(database, batchSize, nodeNames, new UnitOfWork<String>() {
        @Override
        public void execute(GraphDatabaseService database, String nodeName, int batchNumber, int stepNumber) {
            Node node = database.createNode();
            node.setProperty("name", nodeName);
        }
    });

    executor.execute();
```

#### Batch Operations with Generated Input or No Input

In case you wish to do something input-independent, for example just generate a number of nodes with random names, you
can use the `NoInputBatchTransactionExecutor`.

First, you would create an implementation of `UnitOfWork<NullItem>`, which is a unit of work expecting no input:

```java
    /**
     * Unit of work that creates an empty node with random name. Singleton.
     */
    public class CreateRandomNode implements UnitOfWork<NullItem> {
        private static final CreateRandomNode INSTANCE = new CreateRandomNode();

        public static CreateRandomNode getInstance() {
            return INSTANCE;
        }

        private CreateRandomNode() {
        }

        @Override
        public void execute(GraphDatabaseService database, NullItem input, int batchNumber, int stepNumber) {
            Node node = database.createNode();
            node.setProperty("name", UUID.randomUUID());
        }
    }
```

Then, you would use it in `NoInputBatchTransactionExecutor`:

```java
    //create 100,000 nodes in batches of 1,000:
    int batchSize = 1000;
    int noNodes = 100000;
    BatchTransactionExecutor batchExecutor = new NoInputBatchTransactionExecutor(database, batchSize, noNodes, CreateRandomNode.getInstance());
    batchExecutor.execute();
```

#### Multi-Threaded Batch Operations

If you wish to execute any batch operation using more than one thread, you can use the `MultiThreadedBatchTransactionExecutor`
 as a decorator of any `BatchTransactionExecutor`. For example, to execute the above example using 4 threads:

```java
    int batchSize = 1000;
    int noNodes = 100000;
    BatchTransactionExecutor batchExecutor = new NoInputBatchTransactionExecutor(database, batchSize, noNodes, CreateRandomNode.getInstance());
    BatchTransactionExecutor multiThreadedExecutor = new MultiThreadedBatchTransactionExecutor(batchExecutor, 4);
    multiThreadedExecutor.execute();
```

### Improved Transaction Event API

In `com.graphaware.tx.event`, you will find a decorator of the Neo4j Transaction Event API (called `TransactionData`).
Before a transaction commits, the improved API allows users to traverse the new version of the graph (as it will be
after the transaction commits), as well as a "snapshot" of the old graph (as it was before the transaction started).
It provides a clean API to access information about changes performed by the transaction as well as the option to
perform additional changes.

The least you can gain from using this functionality is avoiding `java.lang.IllegalStateException: Node/Relationship has
been deleted in this tx` when trying to access properties of nodes/relationships deleted in a transaction. You can also
easily access relationships/nodes that were changed and/or deleted in a transaction, again completely exception-free.

#### Usage

To use the API, simply instantiate one of the `ImprovedTransactionData` implementations.
`LazyTransactionData` is recommended as it is the easiest one to use.

```java
     GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase();

     database.registerTransactionEventHandler(new TransactionEventHandler<Object>() {
         @Override
         public Object beforeCommit(TransactionData data) throws Exception {
             ImprovedTransactionData improvedTransactionData = new LazyTransactionData(data);

             //have fun here with improvedTransactionData!

             return null;
         }

         @Override
         public void afterCommit(TransactionData data, Object state) {
         }

         @Override
         public void afterRollback(TransactionData data, Object state) {
         }
     });
```

`FilteredTransactionData` can be used instead. They effectively hide portions of the graph, including any changes performed
on nodes and relationships that are not interesting. `InclusionStrategies` are used to convey the information about
what is interesting and what is not. For example, of only nodes with name equal to "Two" and no relationships at all
are of interest, the example above could be modified as follows:

```java
    GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase();

    database.registerTransactionEventHandler(new TransactionEventHandler<Object>() {
        @Override
        public Object beforeCommit(TransactionData data) throws Exception {
            InclusionStrategies inclusionStrategies = InclusionStrategiesImpl.all()
                    .with(new IncludeAllBusinessNodes() {
                        @Override
                        protected boolean doInclude(Node node) {
                            return node.getProperty("name", "default").equals("Two");
                        }

                        @Override
                        public String asString() {
                            return "includeOnlyNodeWithNameEqualToTwo";
                        }
                    })
                    .with(IncludeNoRelationships.getInstance());

            ImprovedTransactionData improvedTransactionData = new FilteredTransactionData(new LazyTransactionData(data), inclusionStrategies);

            //have fun here with improvedTransactionData!

            return null;
        }

        @Override
        public void afterCommit(TransactionData data, Object state) {
        }

        @Override
        public void afterRollback(TransactionData data, Object state) {
        }
    });
```

#### Example Scenario

Let's illustrate why this might be useful on a very simple example. Let's say we have a `FRIEND_OF` relationship in the
system and it has a `strength` property indicating the strength of the friendship from 1 to 3. Let's further assume that
we are interested in the total strength of all `FRIEND_OF` relationships in the entire system.

We'll achieve this by creating a custom transaction event handler that keeps track of the total strength. While not an
ideal choice from a system throughput perspective, let's say for the sake of simplicity that we are going to store the
total strength on the root node (with ID=0) as a `totalFriendshipStrength` property.

```java
public class TotalFriendshipStrengthCounter implements TransactionEventHandler<Void> {
    private static final RelationshipType FRIEND_OF = DynamicRelationshipType.withName("FRIEND_OF");
    private static final String STRENGTH = "strength";
    public static final String TOTAL_FRIENDSHIP_STRENGTH = "totalFriendshipStrength";

    private final GraphDatabaseService database;

    public TotalFriendshipStrengthCounter(GraphDatabaseService database) {
        this.database = database;
    }

    @Override
    public Void beforeCommit(TransactionData data) throws Exception {
        ImprovedTransactionData improvedTransactionData = new LazyTransactionData(data);

        int delta = 0;

        //handle new friendships
        for (Relationship newFriendship : improvedTransactionData.getAllCreatedRelationships()) {
            if (newFriendship.isType(FRIEND_OF)) {
                delta += (int) newFriendship.getProperty(STRENGTH, 0);
            }
        }

        //handle changed friendships
        for (Change<Relationship> changedFriendship : improvedTransactionData.getAllChangedRelationships()) {
            if (changedFriendship.getPrevious().isType(FRIEND_OF)) {
                delta -= (int) changedFriendship.getPrevious().getProperty(STRENGTH, 0);
                delta += (int) changedFriendship.getCurrent().getProperty(STRENGTH, 0);
            }
        }

        //handle deleted friendships
        for (Relationship deletedFriendship : improvedTransactionData.getAllDeletedRelationships()) {
            if (deletedFriendship.isType(FRIEND_OF)) {
                delta -= (int) deletedFriendship.getProperty(STRENGTH, 0);
            }
        }

        Node root = database.getNodeById(0);
        root.setProperty(TOTAL_FRIENDSHIP_STRENGTH, (int) root.getProperty(TOTAL_FRIENDSHIP_STRENGTH, 0) + delta);

        return null;
    }

    @Override
    public void afterCommit(TransactionData data, Void state) {
    }

    @Override
    public void afterRollback(TransactionData data, Void state) {
    }
}
```

All that remains is registering this event handler on the database:

```java
GraphDatabaseService database = new TestGraphDatabaseFactory().newImpermanentDatabase();
database.registerTransactionEventHandler(new TotalFriendshipStrengthCounter(database));
```

`TotalFriendshipStrengthCountingDemo` demonstrates the entire example.

#### Usage in Detail

The API categorizes `PropertyContainer`s, i.e. `Node`s and `Relationship`s into:

* created in this transaction
* deleted in this transaction
* changed in this transaction, i.e those with at least one property created, deleted, or changed
* untouched by this transaction

Users can find out, whether a `PropertyContainer` has been created, deleted, or changed in this transaction and obtain
all the created, deleted, and changed PropertyContainers.

Properties that have been created, deleted, and changed in the transaction are categorized by the changed `PropertyContainer`
 they belong to. Users can find out, which properties have been created, deleted, and changed for a given changed
 `PropertyContainer` and check, whether a given property for a given changed `PropertyContainer` has been created,
 deleted, or changed.

Properties of created `PropertyContainer`s are available through the actual created `PropertyContainer`.
Properties of deleted `PropertyContainer`s (as they were before the transaction started) are available through the
snapshot of the deleted `PropertyContainer`, obtained by calling `getDeleted(org.neo4j.graphdb.Node)` or
`getDeleted(org.neo4j.graphdb.Relationship)`. Properties of created and deleted containers will not be returned by
`changedProperties(org.neo4j.graphdb.Node)` and `changedProperties(org.neo4j.graphdb.Relationship)` as these only return
changed properties of changed `PropertyContainer`s.

Changed `PropertyContainer`s and properties are wrapped in a `Change` object which holds the previous state of the
object before the transaction started, and the current state of the object (when the transaction commits).

All created `PropertyContainer`s and properties and current versions of changed `PropertyContainer`s and properties can
be accessed by native Neo4j API and the traversal API as one would expect. For example, one can traverse the graph starting
 from a newly created node, using a mixture of newly created and already existing relationships. In other words, one can
  traverse the graph as if the transaction has already been committed. This is similar to using TransactionData.

A major difference between this API and `TransactionData`, however, is what one can do with the returned information
about deleted `PropertyContainer`s and properties and the previous versions thereof. With this API, one can traverse a
_snapshot_ of the graph as it was before the transaction started. As opposed to the `TransactionData` API, this will not
result in exceptions being thrown.

For example, one can start traversing the graph from a deleted `Node`, or the previous version of a changed `Node`.
Such traversal will only traverse `Relationship`s that existed before the transaction started and will return properties
and their values as they were before the transaction started. This is achieved using `NodeSnapshot` and `RelationshipSnapshot`
 decorators.

One can even perform additional mutating operations on the previous version (snapshot) of the graph, provided that the
mutated objects have been changed in the transaction (as opposed to deleted). Mutating deleted `PropertyContainer`s and
properties does not make any sense and will cause exceptions.

To summarize, this API gives access to two versions of the same graph. Through created `PropertyContainer`s and/or their
 current versions, one can traverse the current version of the graph as it will be after the transaction commits.
 Through deleted and/or previous versions of `PropertyContainer`s, one can traverse the previous snapshot of the graph,
 as it was before the transaction started.

#### Batch Usage

In case you would like to use the Neo4j `BatchInserter` API but still get access to `ImprovedTransactionData` during
batch insert operations, `TransactionSimulatingBatchInserterImpl` is the class for you. It is a `BatchInserter` but
allows `TransactionEventHandler`s to be registered on it. It then simulates a transaction commit every once in a while
(configurable, please refer to JavaDoc).

As a `GraphDatabaseService` equivalent for batch inserts, this project provides `TransactionSimulatingBatchGraphDatabase`
for completeness, but its usage is discouraged.

### DTOs

In `com.graphaware.propertycontainer.dto`, you will find classes used for creating detached representations (or Data
Transfer Objects (DTOs)) from Nodes, Relationships and Properties.

First, there are property-less representations of Relationships, useful for encapsulating a `RelationshipType` and
(optionally) `Direction`. These can be found in the `common` sub-package. For instance, if you need an object that
encapsulates `RelationshipType` and `Direction`, which can be constructed from a `Relationship` object, use

```java
   Relationship relationship = ... //get it in the database
   HasTypeAndDirection relationshipRepresentation = new TypeAndDirection(relationship); //now you can store this, serialize it, or whatever
```

Of course, other commonly needed constructors are provided. For String-convertible encapsulation of `RelationshipType`
and `Direction`, use `SerializableTypeAndDirectionImpl`, which provides a toString(...) method and a String-based
constructor.

If your relationship representations need to contain properties as well, have a look at the `plain.relationship`
sub-package. Specifically,there are immutable relationship representations, like `ImmutableRelationshipImpl` and
`ImmutableDirectedRelationshipImpl`, and their mutable counterparts. The reason they are in the `plain` package is that
no conversion is done on property values; they are represented as `Object`s.

In the `string.relationship` package, you will find some classes with the same names as above (`ImmutableRelationshipImpl`
and `ImmutableDirectedRelationshipImpl`,...). These are essentially the same except that property values are represented
as Strings. This is useful, for instance, when you want to convert a `Relationship` representation (including its
properties) to and from a single String. For that purpose, use `SerializableRelationshipImpl` and
`SerializableDirectedRelationshipImpl`.

Please note that when using this feature, it would be good not to name anything (properties, relationships) with names
that start with "_GA_". Also, please refrain from using the "#" (or anything else you choose to be your information
separator in string representations of relationships, nodes, and properties) symbol altogether, especially in
relationship and node property values.

Neo4j does not allow null keys for properties, but *does allow* empty Strings to be keys. GraphAware *does not allow this*,
please make sure you don't use empty Strings as property keys. Empty Strings (or nulls) as values are absolutely fine.

### Miscellaneous Utilities

The following functionality is also provided:

* Arrays (see `ArrayUtils`)
    * Determine if an object is a primitive array
    * Convert an array to a String representation
    * Check equality of two `Object`s which may or may not be arrays
    * Check equality of two `Map<String, Object>` instances, where the `Object`-typed values may or may not be arrays

* Property Containers (see `PropertyContainerUtils`)
    * Convert a `PropertyContainer` to a Map of properties
    * Delete nodes with all their relationships automatically, avoiding a `org.neo4j.kernel.impl.nioneo.store.ConstraintViolationException: Node record Node[xxx] still has relationships`, using `DeleteUtils.deleteNodeAndRelationships(node);`

* Relationship Directions
    * The need to determine the direction of a relationship is quite common. The `Relationship` object does not provide the
      functionality for the obvious reason that it depends on "who's point of view we're talking about". In order to resolve
      a direction from a specific Node's point of view, use `DirectionUtils.resolveDirection(Relationship relationship, Node pointOfView);`

* Iterables (see `IterableUtils` in tests)
    * Count iterables by iterating over them, unless they're a collection in which case just return `size()`
    * Randomize iterables by iterating over them and shuffling them, unless they're a collection in which case just shuffle
    * Convert iterables to lists
    * Check if iterable contains an object by iterating over the iterable, unless it's a collection in which case just return `contains(..)`

... and more, please see JavaDoc.

License
-------

Copyright (c) 2013 GraphAware

GraphAware is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with this program.
If not, see <http://www.gnu.org/licenses/>.