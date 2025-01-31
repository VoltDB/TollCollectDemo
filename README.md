# Toll Collection Demo with Volt Active Data
## Introduction

An app to demonstrate the primary components of Volt Active Data and to provide an example of application development with the platform.

The environment is implemented through Docker containers and is run using Docker Compose.

This example lets you simulate a toll collection application for a make-believe organization that has built a number of impressive and imaginary infrastructure projects across the United States. For the convenience of its customers and one-time visitors, this company has chosen to implement license plate recognition (LPR) and computer vision technologies. Once the vehicle is detected and its license plate is scanned at various toll plazas, tunnels, and bridges, it will be the responsibility of the Volt Active Data platform to process the appropriate transactions quickly (in real time) and accurately.

## Directory Structure

### ddl

This directory contains files with SQL statements to create the schema and structures of the database needed before running the application. In this example, the DDL statements will create replicated tables, partitioned tables, streams, materialized views, simple SQL stored procedures, java stored procedures, and table indexes.

### csv

This directory contains csv files to populate the database with initial data before simulating any transactions. The files are:

- Accounts.csv: Accounts, their balances, and other account attributes  
- Known\_vehicles.csv: Registered vehicles, their plate number, owner account, and other attributes.  
- Toll\_locations.csv: A list of toll locations, their geographic data, and their base fare.  
- Vehicle\_types: A list of vehicle types and an associated multiplier applied to a base fare.

### TollCollectProcedures

This directory contains the source code and java classes (when compiled) to handle server-side logic. The workflow is divided into two ACID transactions:

- ProcessPlate: Looks up vehicle and plate information, calculates a toll based on location and vehicle details, records an audit entry of the scan, and conditionally forwards the toll to an external bill-by-mail application for non-account holders.  
- ChargeAccount: Looks up account information, deducts appropriate toll amount, triggers any necessary top up of account balances for account holders that have opted in, and records an audit entry of the account transaction.

### TollCollectClient

This directory contains the source code and executable java application (when compiled) that embeds the client2 java client interface to interact with Volt. For this example, the client application allows a user to manually submit vehicle information to the platform for toll processing.

### TollCollectStreamPipeline

This directory contains the source and java classes (when compiled) of an ActiveSP stream pipeline to simulate the toll application at scale. The directory also includes custom java components that are referenced by the pipeline. This example of a stream processing pipeline is a limited implementation of the application’s business workflow, only processing vehicle information and leaving account charging as a future exercise. The pipeline consists of the following components:

- Generator Source: a source operator that produces streams of synthetic data for the pipeline.  
- Stateless processor: processor that converts a message into a format appropriate for sending to Volt.  
- Volt Sink: a sink operator sending messages to a Volt Stored Procedure. In this case, to “ProcessPlate”.

The directory also includes the following custom java that is used by the pipeline.

- Record class: used by the generator to model messages created by the source.  
- PlateRecordGenerator: custom logic to generate messages according to predefined algorithms.

### dev-edition-app

This directory is for compiled application components and for configuration files needed to bring up the full streaming application through docker compose. This directory is also used to pass configuration values to the application components, including a few predefined grafana dashboards.

### Maven files

The repository also contains pom.xml files to describe the structure of the project, handle dependencies, and to build the application locally from source using the maven build automation tool. If you don’t have maven on your system, the mvnw wrapper script will download maven for you from the maven central repository.

## Prerequisites

To get started, first ensure that you have the prerequisites. You will need:

- Java JDK 17 or 21  
- Docker Desktop  
- Request a [license](https://www.voltactivedata.com/developer-edition/) to run the developer edition of Volt Active Data with ActiveSP

## Running the application

### Running the streaming application and database

Once you have your license, set the LICENSE\_FILE\_PATH environment variable with the location of the license.

```
LICENSE_FILE_PATH=/Users/tkowalcz/license.xml
export LICENSE_FILE_PATH
```

Download the repo contents or clone repo

```
git clone https://github.com/VoltDB/TollCollectDemo
```

Build project

```
cd TollCollectDemo
./mvnw clean package
```

Start the application

```
cd target/dev-edition-app-1.0-SNAPSHOT/dev-edition-app
docker compose up
```

Note: you can use \-d to start the application in the background

### Running the client application

Once your application components are running, you can also use the client application.

```
cd TollCollectClient/target
java -jar TollCollectClient-1.0-SNAPSHOT.jar
```

### Stopping the streaming application and database

Remove the docker containers and clean up volumes

```
docker compose down -v
```

## Next Steps

See more details about getting started with this application and the Volt Active Data Developer Edition by reviewing the [Developer Edition: Quick Start Guide](https://www.voltactivedata.com/developer-edition-quick-start-guide/)  


