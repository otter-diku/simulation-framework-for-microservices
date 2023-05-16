# Work Load Generator 

This component of the simulation framework is
reponsible executing a example scenario
of clients executing transactions / requests
against the microservice system.

The scenario might be specified using 
a small configuration file (json/yaml/txt),
alternatively we might allow for writing
C# functions.

## Workload

A workload consists of different transactions
(a user making series of request to achieve a goal)
and data.

The data for this can be generated randomly.

We need a definition of the transction logic
(literally C# function) and then a config
which specified the number of clients (type of transaction)
and their distribution during execution.

## Entrypoint
The main simulation tool should be able to start the orleans server (silo) in the background
and then submit the workload to the coordinator grain (which will in turn spawn stateless 
client grains / transactions).

## Implementation

We can utilize the virtual actor Orleans framework.
Where a Grain will implement the logic of a specific
transaction.

A central coordinator grain will spawn based on
the configuration the necessary (stateless worker grains)
which execute the request.


We might want to log/persistent the responses the
worker grains get, which will be used for subsequent
invariant checking / statistic generation by the
simulation framework (for checking a invariant we
might need this information?).

### Using Orleans

By using orleans with a coordinator actor and multiple
worker actors we can easily scale to real world scenario
of thousands of clients interacting the the microservice
system. For this the WorkloadGenerator component 
would be deployed across multiple machines using
containers.


### Using custom stress tests

Alternatively our simulation framework should
also be able to work with already existing external
stress tests (not doing its own workload generation),
as for most real world systems such a test suite
probably already exists.
