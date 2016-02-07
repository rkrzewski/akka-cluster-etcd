Akka HTTP based etcd client
===========================

This module provides a fully asynchronous client for etcd 2.x implemented using Akka HTTP.

Note on running tests
---------------------

The integration tests require an instance of etcd running at `localhost:4001`. This can be easily
accomplished using Docker:

```
docker run -d --name etcd --net host quay.io/coreos/etcd:v2.2.0
```
