package algorithm;

import constant.Constant;
import network.Connection;
import util.SemaUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Author: JeffinBao
 * Date: 2019-09-09
 * Usage: Ricart-Agrawala Distributed Mutual Exclusion Algorithm with Optimization
 *        proposed by Carvalho-Roucairol
 */
public class RaAlgoWithCrOptimization {
    // me is the server/process id
    // n is the number of server/process in the system
    // ourSeqNum is the sequence number of current server/process
    // highestSeqNum is the highest sequence number in the system
    private int me, n;
    private volatile int ourSeqNum;
    private volatile int highestSeqNum;
    // using indicates whether current server/process is in C.S., default value is false
    // waiting indicates whether current server/process is waiting to enter C.S., default value is false
    private volatile boolean using = false;
    private volatile boolean waiting = false;
    // authorization is a boolean array indicating whether a server/process j has granted
    // permission for me to enter C.S. without consulting j
    // replyDeferred is a boolean array indicating whether a server/process j's request has
    // been deferred
    private volatile boolean[] authorization;
    private volatile boolean[] replyDeferred;
    // requestSet stores the requests "me" needs to send to other clients in order to enter C.S.
    // every time "me" receives a reply from other client, remove that clientId from the requestSet
    // and check whether the requestSet is empty. If it's empty, it can enter C.S.
    private Set<Integer> requestSet = new HashSet<>();
    // store the requests "me" has sent to other clients. It is used for avoiding duplicate request sent
    // to other clients.
    private Set<Integer> requestSentSet = new HashSet<>();
    private Map<Integer, Connection> clientConnMap = new HashMap<>();
    // distinguish different file, each file should have a RAAlgoWithCROptimization object
    private int fileId;
    private Semaphore mutex = new Semaphore(1);
    private Map<Integer, Connection> deferMap = new HashMap<>();
    private CriticalSectionCallback csCallback;

    public RaAlgoWithCrOptimization(int me, int n, int fileId,
                                    Map<Integer, Connection> clientConnMap,
                                    CriticalSectionCallback csCallback) {
        this.me = me;
        this.n = n;
        this.fileId = fileId;
        this.clientConnMap = clientConnMap;
        this.csCallback = csCallback;
        authorization = new boolean[n];
        replyDeferred = new boolean[n];
    }

    /**
     * handle request msg
     * @param connection connection
     * @param theirSeqNum their sequence number
     * @param j their client id
     * @param otherReqNum their request number
     */
    public void treatRequestMsg(Connection connection, int theirSeqNum, int j, int otherReqNum) {
        System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId + " -- request from client " + j +
                " to " + me + " theirSeqNum is: " + theirSeqNum);
        SemaUtil.wait(mutex);
        System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId + " enter mutex position 1");
        highestSeqNum = Math.max(highestSeqNum, theirSeqNum);
        boolean ourPriority = theirSeqNum > ourSeqNum || (theirSeqNum == ourSeqNum && j > me);
        System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId +
                " -- ourSeqNum: " + ourSeqNum + "using: " + using + " waiting: " + waiting + " ourPriority: " + ourPriority);
        // defer sending reply msg to jth server
        if (using || (waiting && ourPriority)) {
            replyDeferred[j] = true;
            deferMap.put(j, connection);
            System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId +
                    " -- defer sending reply from client " + me + " to client " + j);
        }

        // (waiting && !authorization[j] && !ourPriority) means we don't need to consult jth server/process
        // because invoking treatReqMsg function, we don't have permission from j and have already sent consulting request to j
        if (!(using || waiting) || (waiting && !authorization[j] && !ourPriority)) {
            authorization[j] = false;
            String reply = Constant.REPLY_ME + " " + me + " " + fileId;
            connection.writeUTF(reply);
            System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId +
                    " -- reply position 0: send mutual exclusion reply from client " + me + " to client " + j +
                    " content: " + reply);
        }

        // consult jth server/process, since before invoking treatReqMsg function, we have permission from j
        // however, we don't have priority, hence permission becomes invalid, we need to consult j immediately
        if (waiting & authorization[j] & !ourPriority) {
            authorization[j] = false;
            String reply = Constant.REPLY_ME + " " + me + " " + fileId;
            connection.writeUTF(reply);
            System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId +
                    " -- reply position 1: send mutual exclusion reply from client " + me + " to client " + j +
                    "content: " + reply);

            if (!requestSentSet.contains(j)) {
                requestSet.add(j);
                requestSentSet.add(j);
                String request = Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + fileId + " " + otherReqNum;
                clientConnMap.get(j).writeUTF(request);
                System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId +
                        " -- request position 1: send mutual exclusion request from client " + me + " to client " + j +
                        "content: " + request);
            }
        }

        SemaUtil.signal(mutex);
        System.out.println("otherRequestNum: " + otherReqNum + " file " + fileId + " exit mutex position 1, finish treatRequestMsg");
    }

    /**
     * handle reply msg
     * @param otherClientId their client id
     */
    public void treatReplyMsg(int otherClientId) {
        System.out.println("file " + fileId + " -- Get the permission from client " + otherClientId + " to " + me);
        SemaUtil.wait(mutex);
        System.out.println("file " + fileId + " enter into mutex position 2");
        authorization[otherClientId] = true;
        requestSet.remove(otherClientId);
        System.out.println("file " + fileId + " -- further permissions need to receive: " + requestSet.size());
        if (requestSet.size() == 0) {
            System.out.println("file " + fileId + " -- Get all permissions from other client, enter into critical section to " + fileId + " ourSeqNum: " + ourSeqNum);
            waiting = false;
            using = true;
            SemaUtil.signal(mutex);
            System.out.println("file " + fileId + " exit mutex position 2");
            csCallback.enterCS();
        } else {
            SemaUtil.signal(mutex);
            System.out.println("file" + fileId + "--exit mutex position 2-1");
        }
    }

    /**
     * request resource from other client-side server
     * @param opType operation type
     * @param requestNum request number
     */
    public void requestResource(String opType, int requestNum) {
        System.out.println("Request " + requestNum + " file " + fileId + " current opType: " + opType);
        SemaUtil.wait(mutex);
        waiting = true;
        ourSeqNum = highestSeqNum + 1;
        System.out.println("Request " + requestNum + " file " + fileId + " enter into mutex position 3 -- ourSeqNum: " + ourSeqNum + " client id: " + me);

        for (int id : clientConnMap.keySet()) {
            System.out.println("Request " + requestNum + " file " + fileId + " -- all connections: " + id + " current opType: " + opType);
            if (!authorization[id]) {
                System.out.println("Request " + requestNum + " file " + fileId + " -- connection needs to ask authorization: " + id);
                if (!requestSentSet.contains(id)) {
                    requestSet.add(id);
                    requestSentSet.add(id);
                    String request = Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + fileId + " " + requestNum;
                    System.out.println("Request " + requestNum + " file " + fileId + " -- request position 0, content: " + request +
                            " send mutual exclusion request from client " + me + " to client " + id
                            + " ourSeqNum: " + ourSeqNum + " highSeqNum: " + highestSeqNum);
                    clientConnMap.get(id).writeUTF(request);
                }
            }
        }

        // if we don't need to ask permission, enter into critical section directly
        if (requestSet.size() == 0) {
            System.out.println("Request " + requestNum + " file " + fileId + "--Time when we don't need to ask permission");
            waiting = false;
            using = true;
            SemaUtil.signal(mutex);
            System.out.println("Request " + requestNum + " file " + fileId + " exit mutex position 3");
            csCallback.enterCS();
        } else {
            SemaUtil.signal(mutex);
            System.out.println("Request " + requestNum + " file " + fileId + " exit mutex position 3-1");
        }
    }

    /**
     * release resource after operation finishes
     */
    public void releaseResource() {
        SemaUtil.wait(mutex);
        System.out.println("file " + fileId + " enter mutex position 6");
        using = false;
        for (int i = 0; i < n; i++) {
            if (replyDeferred[i]) {
                replyDeferred[i] = false;
                authorization[i] = false;
                // send reply msg to other server/process
                String reply = Constant.REPLY_ME + " " + me + " " + fileId;
                Connection connection = deferMap.get(i);
                connection.writeUTF(reply);
                System.out.println("file " + fileId + " -- Send deferred reply to client " + i + " from " + me +
                        "content: " + reply);
            }
        }

        // send all deferred reply, remove all sockets
        deferMap.clear();
        // clear request sent set
        requestSentSet.clear();
        SemaUtil.signal(mutex);
        System.out.println("file " + fileId + " exit mutex position 6");
        // use callback to tell the client one operation finishes
        csCallback.finishCS();
    }
}
