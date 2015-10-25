Akka cluster management using etcd
==================================

[![Travis Widget]][Travis]

[Travis]: https://travis-ci.org/rkrzewski/akka-cluster-etcd
[Travis Widget]: https://travis-ci.org/rkrzewski/akka-cluster-etcd.svg?branch=master

Running an [Akka cluster](http://doc.akka.io/docs/akka/2.4.0/common/cluster.html) requires
establishing a set of _seed nodes_. The purpose of them is two-fold: all nodes joining the cluster
do so by contacting any of the seed nodes over the network, and one of the seed nodes bootstraps
the cluster by "joining itself" - establishing a new cluster with size 1.

When running a distributed application on a cloud platform, the IP address of VM/container where
a particular application module will execute is generally not known beforehand. This creates an
administrative burden: first a an initial node needs to be launched to bootstrap the cluster, then
an appropriate number of secondary seed nodes need to be launched and join the initial seed, then
finally ordinary nodes may be launched using full seed list. Collection of IP addresses of running
nodes and passing around is a process that begs to be automated.  

There are several cloud application platforms based on Docker ([Kubernetes](http://kubernetes.io/),
[Fleet](https://coreos.com/using-coreos/clustering/)) where [etcd](https://coreos.com/etcd/) is
used as a distributed configuration store that is used by the platform for workload scheduling and
maintaining SDN ([Weave](http://zettio.github.io/weave/),
[Flannel](https://github.com/coreos/flannel)). etcd instance used by the platform is not available
to the managed applications, however an etcd instance (or a cluster) may be started also as an
application module. etcd can be then used to elect the initial seed node and publishing a list of
seed nodes afterward. This enables a zero-configuration deployment scenario for Akka cluster.

## Examples

* [Cluster Monitor] – a minimal demo application that boots an empty Akka cluster and stays idle until VM is terminated
* [Docker Compose] – Cluster Monitor running inside Docker Compose containers

[Cluster Monitor]: examples/cluster-monitor
[Docker Compose]: examples/docker-compose
