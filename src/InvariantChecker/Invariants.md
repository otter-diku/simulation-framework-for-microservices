# Invariants for the eShopOnContainers application

Here we already list some invariants one might want to check
with our tool (quantify if they are violated).


From reading the HawkEDA paper we might really focus on
event-ordering, event-based constraints. Because
we assume that microservices use Kafka (or other EDA infrastructure)
for communication and since each service has his own private state 
the order in which the events arrive is decisive for violating invariants
or not.

## General notes about invariants
- goes back to hoare paper (axioms)
- invariant expresses application correctness criteria
- can often be expressed in logic (First order, temporal, LTL, other formal systems)

## Possible Invariants for eShopOnContainers
- price update against catalog service is reflected in basket

- if basket is using Redis for pesistence meaning the customer 
  basket is saved here across client sessions?
  What happens if item is deleted and the client logs out and in again
  --> meaning at which point in time does Basket service remove
     that item?
     
-  
