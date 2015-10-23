Running cluster monitor using Docker Compose
============================================

Docker Compose allows building multi-container applications and running them 
on the local machine, resources permitting) It's easy to configure and 
operate, making it a very useful tool for development.

Prerequisites
-------------

   * [Docker](https://docs.docker.com/installation/)
   * [Docker Compose](https://docs.docker.com/compose/install/)
   * [Cluster monitor image](../cluster-monitor/README.md) built and published to local Docker
     repository

Running the example
-------------------

In order to start application on your machine, navigate your shell to this 
directory in git repository clone on your disk. Then it's advisable to set
`COMPOSE_PROJECT_NAME` environment variable to something sensible because
by default, Docker Compose will derive it from directory name. Let's assume
```
export COMPOSE_PROJECT_NAME=example
```
Then run:
```
docker-compose up -d
```
This will launch etcd server and a single Akka cluster node. You can spawn 3 more 
nodes like this:
```
docker-compose scale monitor=4
```
You can can observe the new nodes joining the cluster by viewing logs thorough
Docker / Docker Compose log facility:
```
docker-commpose logs
```
Then, open a second terminal window and stop the original cluster leader:
```
docker stop example_monitor_1
```
In the terminal window streaming application logs, you should observe the node
being removed from the cluster and another node taking over leader role. You
can then restart the node and obsere it re-joning the cluster:
```
docker start example_monitor_1
```
When you are done, run the following commands to stop and discard the 
containers:
```
docker-compose stop
docker-compose rm
```

