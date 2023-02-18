In this document I try to list and model the possible
customer interactions with the eshop in form of transactions,
will should then be executed by one grain (thread) at a time.


In our modified version of eShopOnContainers we mostly care
about:

- adding items to basket/chart
- order creation
- add / change catalog
  - add new items
  - change price of items


In a a first simplified version we directly issue requests
against the microservice APIs, in a more realistic scenario
we can try to emulate a client web frontend (MVC or SPA)
then we issue requests against the Backend for Frontend BFF
APIs which in turn get translated into a mix of HTTP and 
gRPC requests by the API aggregator / API gateway.

## Basket 
The basket contains temporal data about the product
items a customer has added to his basket,
including the product price and time when it was added
to the basket.


## Catalog
The catalog contains the shop items, items can be deleted



## Clients

### Update catalog
- add item
- delete item
- update price of item

### Customer Basket
- add items to basket
- delete items
- proceed to checkout
