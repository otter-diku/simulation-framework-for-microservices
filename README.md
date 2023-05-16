simulation-framework-for-microservices
======================================

[![build and test](https://github.com/otter-diku/simulation-framework-for-microservices/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/otter-diku/simulation-framework-for-microservices/actions/workflows/build-and-test.yml)


## Specifying invariant queries

Client.main arguments:

```
-invariants <your-path>/simulation-framework-for-microservices/invariant-queries/eshop
-queue-config <your-path>/simulation-framework-for-microservices/invariant-queries/eshop/kafkaConfig.json
-output <your-path>/simulation-framework-for-microservices/output
```

You have to create the kafkaConfig.json file because it contains secrets:

``` json
{
  "broker": "pkc-xmzwx.europe-central2.gcp.confluent.cloud:9092",
  "maxLatenessOfEventsSec": 5,
  "username": "<username>",
  "password": "<secret>"
}

```


## CDC with Debezium

When writing invariants for microservice applications that do not use Kafka for
communication between microservices but instead RPC (gRPC, thrift etc.) like
https://github.com/delimitrou/DeathStarBench/tree/master/socialNetwork
we can utilize change data capture to expose events to the databases of each
microservice and still check invariants.

After starting Zookeeper and Kafka with

``` shell
docker compose up
```

we can start the mongoDB  connector using the mongodb-source.json configuration file:

``` shell
curl -i -X POST -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/ -d @mongodb-source.json
```

Afterwards kafka should contain the following topic "dbserver1.user.user"

When we now create a new example user in the socialNetwork application called
john doe debezium creates the following message (I only include the "payload", here
there is also a lot of schema information as part of the debezium message

``` json
{
  "payload": {
    "before": null,
    "after": "{\"_id\": {\"$oid\": \"6440277b1be32b1565328035\"},\"user_id\": {\"$numberLong\": \"1221160191339040768\"},\"first_name\": \"john\",\"last_name\": \"doe\",\"username\": \"john.doe\",\"salt\": \"D4TderGD3qY60b24xKoxMJccc2sYeY70\",\"password\": \"708a807a360abe4b478edf165266aa32564d66f74f054b238c3640428690fb6e\"}",
    "patch": null,
    "filter": null,
    "updateDescription": null,
    "source": {
      "version": "2.1.4.Final",
      "connector": "mongodb",
      "name": "dbserver1",
      "ts_ms": 1681926011000,
      "snapshot": "false",
      "db": "user",
      "sequence": null,
      "rs": "rs0",
      "collection": "user",
      "ord": 1,
      "lsid": null,
      "txnNumber": null
    },
    "op": "c",
    "ts_ms": 1681926011422,
    "transaction": null
  }
}
```

## Logging
We use  https://datalust.co/seq for logging, this requires running seq in docker:

> docker run --name seq -d --restart unless-stopped -e ACCEPT_EULA=Y -p 5341:80 datalust/seq:latest


## Workload Generation

The tool allows to describe workloads using json configuration files.

Our configuration language consists of the following components:

- operation files ("op_" prefix)
- transaction files ("tx_" prefix)
- workload files ("workload_" prefix)

### Operation files

For now our focus is on microservice applications that use a client facing HTTP API, therefore our
assumption is that transactions ("unit of work a client wants to perform against the application")
consists of multiple HTTP request, to stay general and allow in the future for other protocols (e.g. gRPC)
we model such individual as an operation.

An example operation file looks as follows:

``` json
{
  "templateId": "catalog-get-item-by-id",
  "type": "http",
  "httpMethod": "get",
  "returnValues": [
    {
      "key": "item-1",
      "value": "$",
      "type": "object"
    }
  ],
  "url": "http://localhost:5101/catalog-api/api/v1/Catalog/items/{{product-id}}"
}
```

### Transaction files

A transaction may consist of multiple operations additionally it may take
arguments that are to be provided to all its arguments (e.g. a user-id that
is to be used in all operations).

An example transaction file looks as follows:

``` json
{
  "id": "add-items-to-basket",
  "arguments": [
    {
      "name": "user-id",
      "type": "string"
    }
  ],
  "operations": [
    {
      "operationReferenceId": "catalog-get-items",
      "id": "op-1"
    },
    {
      "operationReferenceId": "basket-add-item",
      "id": "op-2"
    }
  ]
}
```

Together transactions and operations model the scenario of an application,
meaning they describe what kind of actions are possible in the system,
which requests are performed by users etc.

Next we describe workload files which contain parameters for a concrete
execution of requests / workload against the running microservice application.

### Workload files

As the tool is centered around quantifying and detecting data integrity violations,
it needs to be able to generate a realistic workload and give configuration options
to easily express workload characteristics which are of interest. In the case of data integrity
such characteristics are for example data skewness, number of concurrent requests
and fine-grain control over which transactions are executed.

For this we use the notion of **generators** which have an associated distribution (e.g. uniform, zipfian)
for providing transactions with data that matches the desired workload characteristic.

Workload files consist of possibly multiple generators and a list of transactions which have
an associated count (i.e. number of executions).

An example workload file looks as follows:

``` json
{
  "id": "workload-id",
  "generators": [
    {
      "id": "userId",
      "type": "string",
      "sampleSpace": [
        "1000-00000-00000000",
        "1000-00000-00000001",
        "1000-00000-00000002",
        "1000-00000-00000003",
        "1000-00000-00000004",
        "1000-00000-00000005",
        "1000-00000-00000006",
        "1000-00000-00000007",
        "1000-00000-00000008"
        "1000-00000-00000009"
      ],
      "distribution": "zipfian"
    },
    {
      "id": "productId",
      "type": "unsignedInt",
      "distribution": "uniform",
      "min": 1,
      "max": 1000
    }
  ],
  "transactions": [
    {
      "transactionReferenceId": "add-items-to-basket",
      "count": 10000,
      "data": [
        {
          "name":  "user-id",
          "generatorReferenceId": "userId"
        }
      ]
    },
    {
      "transactionReferenceId": "update-catalog-item-price",
      "count": 1000
    }
  ]
}
```
