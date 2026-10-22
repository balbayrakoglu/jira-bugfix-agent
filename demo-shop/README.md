# demo-shop

A small order service used as the target repository for the [AI Bugfix Agent](../README.md). It
contains two intentional bugs, both uncovered by the existing tests (so `mvn verify` is green
before the agent touches anything):

1. `OrderService.discountFor` throws a `NullPointerException` when an order has no coupon
   (`coupon: null`).
2. An order that totals exactly **50.00 €** after discount is charged 4.99 € shipping instead of
   qualifying for free shipping.

```bash
mvn verify
mvn spring-boot:run
curl -X POST localhost:8080/orders/total -H 'Content-Type: application/json' \
  -d '{"id":"1","customerId":"c","lines":[{"sku":"X","quantity":1,"unitPrice":50.00}],"coupon":null}'
```
