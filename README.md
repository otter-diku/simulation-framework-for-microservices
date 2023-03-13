# simulation-framework-for-microservices
======================================

[![build and test](https://github.com/otter-diku/simulation-framework-for-microservices/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/otter-diku/simulation-framework-for-microservices/actions/workflows/build-and-test.yml)



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
