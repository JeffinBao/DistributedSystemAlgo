# Distributed System Algorithms

## Network Topology
![Network Topology](https://raw.githubusercontent.com/JeffinBao/DistributedSystemAlgo/dev/diagram/pic/Network_Topology.png)
### Description
The network topology contains two parts:
  - Client-side server: directly interacts with inbound request. All client-side servers are inter-connected with each other.
  - Backend server: interacts with client-side server, not seen by the inbound request.

### Workflow
  - A request arrives at any client-side server.
  - Client-side server sends out **REQUEST** message to other client-side servers.
  - Client-side server receives **REPLY** from other client-side servers. An agreement reached regarding which client-side server enters into critical section to interact with files stored in backend servers.
  - Client-side server sends **WRITE** or **READ** request to backend servers.
  - After receiving responses from all backend servers, client-side server will send **RELEASE**.

## Client-side Server Architecture
![Client-side Server Architecture](https://raw.githubusercontent.com/JeffinBao/DistributedSystemAlgo/dev/diagram/pic/Client_Side_Server_Architecture.png)

### Description
For each client-side server, I separated inbound and outbound message handling. Each connection from other client-side servers have their own threads to handle inbound and outbound message. The purpose of this design is to increase the throughput and decouple the code.

Also, each file has a distributed mutual exclusion algorithm object to ensure only one client can successfully **WRITE** or **READ**.

## Distributed Mutual Exclusion
### Assumptions
  - Supporting three types of operations:
      - `WRITE`: write to all replicas in backend servers.
      - `READ`: read from one of the replicas in backend servers.
      - `ENQUIRY`: A request from a client for information about the list of hosted files.
  - Each client-side server only handle **one** request at a time.
  - Assume that each file is replicated on all the servers, and all replicas of a file are consistent in the beginning.
  - Assume that the set of file does not change during the program’s execution. Also, assume that no server failure occurs during the execution of the program.


### Algorithms
#### Lamport Distributed Mutual Exclusion
##### Algorithm outline:
  - **REQUEST** from client-side server `i`:
      -  Sends **REQUEST(timestamp, i)** to all other client-side servers.
      -  Adds **REQUEST(timestamp, i)** to its request queue sorted by timestamp(I used PriorityQueue in implementation).
      -  Enters into critical section on receiving **messages(not only reply message)** from all other client-side servers, and find its **REQUEST** at the top of its request queue.
  - **REPLY** from client-side server `j` to `i`:
      - Sends timestamped **REPLY** to client-side server `i`.
      - Adds **REQUEST(timestamp, i)** to it request queue sorted by timestamp.
  - **RELEASE** from client-side server `i` to `j`:
      - Sends timestamped **RELEASE** to all other client-side servers and removes its request from request queue.
      - Client-side server `j` receives **RELEASE** message, then removes `i`'s request from `j`'s request queue.

##### Example
![Lamport Mutual Exclusion](https://raw.githubusercontent.com/JeffinBao/DistributedSystemAlgo/dev/diagram/pic/Lamport_Mutual_Exclusion.png)

##### Analysis
  - Number of messages: **3x(N - 1)**.
  - Synchronization delay: one message propagation delay.
  - Optimization: Client-side server `j` can suppress a **REPLY** to `i` if `j` has already sent a request to `i` with a higher timestamp.

#### Ricart-Agrawala Algorithm with Optimization proposed by Carvalho-Roucairol

##### Algorithm outline:
  - **REQUEST** from client-side server `i`:
      - Sends **REQUEST(timestamp, i)** to all other client-side servers.
      - Enters into critical section on receiving all **REPLY** from other client-side servers. And `i` can hold the permission from `j` until `i` sends a **REPLY** to `j`. This means `i` doesn't need to send request to ask permission from `j` during this time period.
  - **REPLY** from client-side server `j` to `i`:
      - Sends timestamped **REPLY** to `i` if either of the following conditions satisfies:
        - `j` is neither requesting nor executing its critical section.
        - `j`'s request has a `higher` timestamp(lower priority).
      - Otherwise, `j` defers sending **REPLY**.
  - **RELEASE** from client-side server `i`:
    - Sends all deferred replies, since it will hold some lower priority request's **REPLY**.

##### Example
![RA_with_Optimization](https://raw.githubusercontent.com/JeffinBao/DistributedSystemAlgo/dev/diagram/pic/RA_with_Optimization.png)

##### Analysis
  - Number of messages: less than **2x(N - 1)** for the optimization version.
  - Synchronization delay: one message propagation delay.

## How to Run

### Pre-requisite
  - `iTerm2`: a terminal tool similar to Mac's default terminal. It can split the window horizontally and vertically, which is a perfect choice for experimenting with distributed system.
  - `Java version 1.8`

### Steps
  - `git clone` my repository.
  - `cd` into `DistributedSystemAlgo` directory.
  - Run script:
      - `./here_we_go.sh lamport`: run Lamport mutual exclusion algorithm.
      - `./here_we_go.sh ra_with_optimization`: run Ricart-Agrawala algorithm with optimization proposed by Carvalho-Roucairol.

## Correctness
I tested 10,000 requests per client-side server(total 50,000 requests) and checked all replicas to see whether the files were identical. 
I didn't do a correctness verification because that's not what I am capable of till now.

## Reference
1. [Time, Clocks, and the Ordering of Events in a Distributed System](https://lamport.azurewebsites.net/pubs/time-clocks.pdf)
2. [An Optimal Algorithm for Mutual Exclusion in Computer Networks](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.507.9382&rep=rep1&type=pdf)
3. [On Mutual Exclusion in Computer Networks](https://www.researchgate.net/publication/265077447_On_Mutual_Exclusion_in_Computer_Networks)

