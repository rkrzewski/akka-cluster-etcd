# akka-cluster-etcd
Akka cluster management using etcd

Running an [Akka cluster](http://doc.akka.io/docs/akka/2.3.9/common/cluster.html) requires establishing a set of
_seed nodes_. The purpose of them is two-fold: all nodes joinig the cluster do so by contacting any of the seed 
nodes over the network, and one of the seed nodes bootstraps the cluster by "joining itself" - establishing a new 
cluster with size 1.

This creates an administrative burden: care must be taken to start that only one of the seed nodes will perform 
cluster bootstrap (otherwise a the cluster will become partitioned from the very outset) and secondly the IP 
address list of seed nodes needs to be kept up to date and used whenever a new instance of the application is 
started.

There are several cloud application platforms based on Docker ([Kubernetes](http://kubernetes.io/), 
[Fleet](https://coreos.com/using-coreos/clustering/)) [etcd](https://coreos.com/etcd/) is used as a distributed configuration store that is used by the platform for workload scheduling and maintaining SDN ([Weave](http://zettio.github.io/weave/), [Flannel](https://github.com/coreos/flannel)) but it is also available for applications to use. An instance of etcd can be assumed to run on each of the cluster's nodes (Docker hosts) and to be reachable on a fixed IP address visible in the application Docker container. etcd can be then used to discover existing Akka cluster nodes, and also elect the initial seed node, thanks to consistency guarantees provided by etcd. This enables a zero-configuration deployment scenario for Akka cluster.

