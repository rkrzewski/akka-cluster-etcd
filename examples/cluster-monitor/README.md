Cluster monitor
===============

This is a minimal demo application that boots an empty Akka cluster and stays idle until VM is 
terminated. 

The application is packaged as [Docker](http://docker.com/) image. In order to build the image
locally, run the following command in the project's top level directory:
```
sbt publishLocal clusterMonitor/docker:publishLocal
```
After the build is complete, `docker images` listing should contain 
`caltha/akka-cluster-etcd/monitor` image. When invoked directly using 
`docker run caltha/akka-cluster-etcd/monitor`, the application will complain about not being able
to connect to etcd server running at host `etcd`. See other example directories for different 
ways of running the application in a cluster.


