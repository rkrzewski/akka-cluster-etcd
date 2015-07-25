Akka cluster bootstrap & discrovery using etcd
==============================================

Here's a sketch of the module functionality to be implemented.

The module will provide an 
[Akka extension](http://doc.akka.io/docs/akka/2.3.12/scala/extending-akka.html) that upon 
initialization will attempt to join an already running Akka cluster, or if no cluster is running 
yet will attempt to get the current node elected as the cluster's initial seed. In case of 
successful election, the node will "join itself" thus forming cluster with the size of 1, and 
advertise itself as the seed. In case other node participating in the election wins, current node 
will consult the list of seeds provided by the winner and attempt to join the cluster by contacting
each one in turn until a connection is established.

The election is performed by attempting a `compareAndSet` operation with `prevExists = false` 
option on a `<prefix>/akka/leader` path within etcd key space. The value written will be textual
representation of `akka.actor.Address` of the contending node. 

After creating / joining a cluster, each node will register an instance of 
[Cluster singleton](http://doc.akka.io/docs/akka/2.3.12/scala/cluster-usage.html#Cluster_Singleton)
responsible for maintaining public list of seeds. `ClusterSingletonManager` will ensure that 
exactly one instance of the singleton is active at any given time. The singleton will maintain
a list of seeds by creating nodes under `<prefix>/akka/seeds` in etcd keyspace. It will add entries
when new nodes arrive (until reaching a predefined number of seeds) and remove entries if any of 
the nodes registered as seeds leave the cluster.

When the Akka cluster's leader quits and a new leader is elected, which causes aforementioned 
singleton instance to be activated on the new leader node, the instance will write it's own address
as `<prefix>/akka/leader` entry and will update the contents of `<prefix>/akka/seeds` subtree to
reflect it's knowledge of cluster membership.

When a new node joins the cluster, loses the leader election (`<prefix>/akka/leader` exists) but
`<prefix>/akka/seeds` does not exist, is empty, or no nodes in the seed list can be contacted,
the node must wait and try to repeat the same process. In order to mitigate the failure scenario 
where a node wins the election and becomes inoperable before forming a 2-node cluster where the 
other instance can take over the leadership, it is necessary to create `<prefix>/akka/leader` node 
with a finite TTL, and ensure that the seed management singleton re-creates this entry periodically
preventing it's expiration.

The scheme presented above is not secure against split-brain scenarios where Akka cluster becomes
fragmented into sub-clusters, each with it's own separate leader (and thus seed management 
singleton instance) or where etcd cluster loses consistency at the time of initial seed election, 
letting multiple leaders to be elected, thus forming multiple clusters right from the outset. The 
former is notoriously hard to handle automatically (from what I gather following the discussions on
Akka User List) and generally requires monitoring and human intervention when it occurs and the 
latter might be not be a problem in practice, due to etcd design. Then again, writing 
partition-tolerant distributed systems is HARD. If you wish to be scared/amused with the problems
with widely used distributed software I heartily recommend [Jepsen](https://aphyr.com/tags/Jepsen) 
series on Kyle Kingsbury's blog.
