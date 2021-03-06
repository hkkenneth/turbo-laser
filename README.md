# **WELCOME TO TURBO-LASER**


## Introduction

Turbo-laser is a distributed, fault tolerant database processing architecture for Tika, GATE, Biolark and text deidentification, with JDBC and Elasticsearch export options. It makes use of the Spring Batch framework in order to provide a fully configurable pipeline with the goal of generating a JSON that can be readily indexed into elasticsearch. In the parlance of the batch processing [domain language](http://docs.spring.io/spring-batch/reference/html/domain.html), it uses the partitioning concept to create 'partition step' metadata for a DB table. This metadata is persisted in the Spring database schema, whereafter each partition can then be executed locally or farmed out remotely via a JMS middleware server (only ActiveMQ is suported at this time). Remote worker JVMs then retrieve metadata descriptions of work units. The outcome of processing is then persisted in the database, allowing robust tracking and simple restart of failed partitions.

## Why does this project exist/ why is batch processing difficult?

This project was developed as the central 'glue' to combine a variety of processes from a wider architecture known as the 'CogStack'. The CogStack is a range of technologies designed to to support modern, open source healthcare analytics within the NHS, and is chiefly comprised of the Elastic stack (elasticsearch, kibana etc), GATE and Biolark (clinical natural language processing for entity extraction), OCR, clinical text de-identification (Manchester De-ID and the ElasticGazetteer), and Apache Tika for MS Office to text conversion. When processing very large datasets (10s - 100s of millions rows of data), it is likely that some problems will present certain difficulties for different processes. These problems are typically hard to predict - for example, some documents may have very long sentences, an unusual sequence of characters, or machine only content. Such circumstances can create a range of problems for NLP algorithms, and thus a fault tolerant batch frameworks are required to ensure robust, consistent processing.

## Example usage

The entire process is run through the command line, taking a path to a directory containing config files as a single argument. These config files selectively activate Spring profiles as required to perform required data selection, processing and output writing steps.

Examples of config file are in the exampleConfigs dir. Most are relatively self explanatory, or should be annotated to explain their meaning.


example configs can be generated from the gradle task:

```
gradle writeExampleConfig
```

The behaviour of turbo-laser is configured by activating a variety of spring profiles (again, in the config files - see examples) as required. Currently. the available profiles are

processes

 1. tika - process JDBC input with Tika. Extended with a custom PDF preprocessor to perform OCR on scanned PDF document.  (requires ImageMagick and Tesseract on the PATH)
 2. gate - process JDBC input with a generic GATE app.
 3. dBLineFixer - process JDBC input with dBLineFixer (concatenates multi-row documents)
 4. basic - a job without a processing step, for simply writing JDBC input to elasticsearch
 5. deid - deidentify text with a GATE application (such as Azad Dehghan's DEID) or using the ElasticGazetteer - query a database for identifiers and mask them in free text using Levenstein distance
 6. biolark - enable Biolark, Tudor Groza's awesome HPO term extraction project. Note - requires a nested mapping to be set up in elasticsearch, so that the inner JSONs can be queried correctly. See https://www.elastic.co/guide/en/elasticsearch/guide/current/nested-objects.html for details.

scaling
 1. localPartitioning - run all processes within the launching JVM
 2. remotePartitioning - send partitions to JMS middleware, to be picked up by remote hosts (see below)

outputs
 1. elasticsearch - write to an elasticsearch cluster
 2. jdbc - write the generated JSON to a JDBC endpoint. Useful if the selected processes are particularly heavy (e.g. biolark), so that data can be reindexed without the need for reprocessing

partitioning
 1. primaryKeyPartition - process all records based upon partitioning of the primary key
 2. primaryKeyAndTimeStampPartition - process all records based upon partitioning of the primary key and the timestamp, for finer control/ smaller batch sizes per job. Use the processingPeriod property to specify the number of milliseconds to 'scan' ahead for each job run

## Scheduling
Turbo-laser also offers a built in scheduler, to process changes in a database between job runs (requires a timestamp in the source database)
```
useScheduling = true
```
run intervals are handled with the following CRON like syntax
```
scheduler.rate = "*/5 * * * * *"
```


## Logging support

Turbo-laser uses the SLF4J abstraction for logging, with logback as the concrete implementation. To name a logfile, simply add the -DLOG_FILE_NAME system flag when launching the JVM

e.g.

```
java -DLOG_FILE_NAME=aTestLog -DLOG_LEVEL=debug -jar turbo-laser-0.3.0.jar /my/path/to/configs
```

Turbo-laser assumes the job repository schema is already in place in the DB implementation of your choice (see spring batch docs for more details)

## Scaling

To add additional JVM processes, whether locally or remotely (via the magic of Spring Integration), just launch an instance with the same config files but with useScheduling = slave. You'll need an ActiveMQ server to co-ordinate the nodes (see config example for details)

That's it! If a job fails, any uncompleted partitions will be picked up by the next run. If a Job ends up in an unknown state (e.g. due to hardware failure), the next run will mark it as abandonded and recommence from the last successful job it can find in the repository.

## JDBC output/reindexing

Using the JDBC output, it is possible to generate a column of JSON strings back into a database. This is useful for reindexing large quantities of data without the need to re-process with the more computationally expensive item processors (e.g. OCR, biolark). To reindex, simply use the reindexColumn in the configuration file. Note, if you include other profiles, these will still run, but will not contribute to the final JSON, and are thus pointless. Therefore, only the 'basic' profile should be used when reindexing data.

## History

This project is an ‘evolution’ of an earlier KHP-Informatics project I was involved with called [Cognition](https://github.com/KHP-Informatics/Cognition-DNC). Although Cognition had an excellent implementation of Levenstein distance for string substitution (thanks [iemre](https://github.com/iemre)!), the architecture of the code suffered some design flaws, such as an overly complex domain model and configuration, and lack of fault tolerance/job stop/start/retry logic. As such, it was somewhat difficult to work with in production, and hard to extend with new features. It was clear that there was the need for a proper batch processing framework. Enter Spring Batch and a completely rebuilt codebase, save a couple of classes from the original Cognition project. Turbo-laser is used at King's College Hospital and the South London and Maudsley Hospital to feed Elasticsearch clusters for business intelligence and research use cases

Some of the advancements in Turbo-laser:

 1. A simple <String,Object> map, with a few pieces of database metadata for its [domain model](https://github.com/RichJackson/turbo-laser/blob/master/src/main/groovy/uk/ac/kcl/model/Document.groovy) (essentially mapping a database row to a elasticsearch document, with the ability to embed [nested types](https://www.elastic.co/guide/en/elasticsearch/reference/2.3/nested.html)
 2. A composite [item processor configuration](https://github.com/RichJackson/turbo-laser/blob/master/src/main/java/uk/ac/kcl/itemHandlers/ItemHandlers.java), that can be easily extended and combined with other processing use case
 3. Complete, sensible coverage of stop, start, retry, abandon logic
 4. A custom socket timeout factory, to manage network failures when the pesky JDBC driver implementations aren’t fully implemented. Check out [this blog post](https://social.msdn.microsoft.com/Forums/office/en-US/3373d40a-2a0b-4fe4-b6e8-46f2988debf8/any-plans-to-add-socket-timeout-option-in-jdbc-driver?forum=sqldataaccess) for info.
 5. The ability to run multiple batch jobs (i.e. process multiple database tables within a single JVM, each having its own Spring container
 6. Remote partitioning via an ActiveMQ JMS server, for complete scalability
 7. Built in job scheduler to enable near real time synchronisation with a database

Questions? Want to help? Drop me a message!
