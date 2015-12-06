# Running a local Kubernetes cluster

If you don't have a Kubernetes cluster at hand, you can easily spin up a local instance for testing.
This directory contains a `docker-compose.yml` file that encapsulates example setup from [Kubernetes documentation](http://kubernetes.io/v1.1/docs/getting-started-guides/docker.html)
You can spin it up by running:
```
docker-compose up
```
Don't be alarmed if you Gnome Terminal (or equivalent) starts acting funny after running this command.
If you get the following error message when trying to open a new terminal tab:
```
There was an error creating the child process for this terminal
getpt failed: Permission denied
```
it means that kubernetes master (running in a privileged container with important system directories
bind-mounted into it) "fixed" the permissions on pseudoterminal multiplexer. You can change them back
by running:
```
sudo chmod a+rw /dev/pts/ptmx
```
Here's some [background info](https://www.kernel.org/doc/Documentation/filesystems/devpts.txt) if you are interested.

You need `kubectl` binary to interact with the cluster. You can download it from here:
```
### Darwin
wget https://storage.googleapis.com/kubernetes-release/release/v1.1.2/bin/darwin/amd64/kubectl

### Linux
wget https://storage.googleapis.com/kubernetes-release/release/v1.1.2 /bin/linux/amd64/kubectl
```
Make the file executable (`chmod +x kubectl`) and place it on `$PATH` (`~/bin` or `/usr/local/bin`)

When setting up the local cluster for the first time, you need to configure `kubectl` to use it:
```
kubectl config set-cluster local --server=http://localhost:8080
kubectl config set-context local --cluster=local
kubectl config use-context local
```
You can use `kubectl config use-context` to switch between different local and remote clusters as needed.

To check if the cluster status started correctly run `kubectl get nodes`. The output should look like:
```
NAME        LABELS                             STATUS    AGE
127.0.0.1   kubernetes.io/hostname=127.0.0.1   Ready     1m
```

When you are done with experimenting you can tear down the cluster by hitting `^C` in console where
`docker-compose up` is running.
