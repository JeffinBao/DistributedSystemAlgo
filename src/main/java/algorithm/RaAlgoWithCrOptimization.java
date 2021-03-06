package algorithm;

import constant.Constant;
import network.Connection;
import network.server.MutualExclusionClient;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-09-09
 * Usage: Ricart-Agrawala Distributed Mutual Exclusion Algorithm with Optimization
 *        proposed by Carvalho-Roucairol
 */
public class RaAlgoWithCrOptimization extends MutexBase {
    // me is the server/process id
    // n is the number of server/process in the system
    // ourSeqNum is the sequence number of current server/process
    // highestSeqNum is the highest sequence number in the system
    private int n;
    private volatile int ourSeqNum;
    private volatile int highestSeqNum;
    private int curRequestNum;
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

    public RaAlgoWithCrOptimization(int me, int n, int fileId,
                                    Map<Integer, Connection> clientConnMap,
                                    MutualExclusionClient client,
                                    LinkedBlockingQueue<String> inboundMsgBlockingQueue,
                                    Map<Integer, LinkedBlockingQueue<String>> outboundBlockingQueueMap) {
        super(me, fileId, clientConnMap, client, inboundMsgBlockingQueue, outboundBlockingQueueMap);
        this.n = n;
        authorization = new boolean[n];
        replyDeferred = new boolean[n];
    }

    /**
     * handle inbound message retrieved from blocking queue
     * @param msg inbound message
     */
    protected void handleMsg(String msg) {
        String[] split = msg.split(" ", 2);
        switch (split[0]) {
            case Constant.REQ_ME: {
                inboundMsgCount++;
                // Format: fileId + Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + requestNum
                String[] split1 = split[1].split(" ");
                int otherSeqNum = Integer.parseInt(split1[0]);
                int otherClientId = Integer.parseInt(split1[1]);
                int otherRequestNum = Integer.parseInt(split1[2]);
                treatRequestMsg(otherSeqNum, otherClientId, otherRequestNum);
                break;
            }
            case Constant.REPLY_ME: {
                inboundMsgCount++;
                // Format: fileId + " " + Constant.REPLY_ME + " " + me
                int otherClientId = Integer.parseInt(split[1]);
                treatReplyMsg(otherClientId);
                break;
            }
            case Constant.FINISH_READ:
            case Constant.FINISH_WRITE: {
                releaseResource();
                break;
            }
            case Constant.INIT_REQUEST: {
                requestResource(Integer.parseInt(split[1]));
                break;
            }
        }
    }

    /**
     * handle request msg
     * @param theirSeqNum their sequence number
     * @param j their client id
     * @param otherReqNum their request number
     */
    private void treatRequestMsg(int theirSeqNum, int j, int otherReqNum) {
        String logBaseInfo = "otherRequestNum: " + otherReqNum + " file " + fileId;
        logger.trace( logBaseInfo + "--request from client " + j +
                " to " + me + " theirSeqNum is: " + theirSeqNum);
        highestSeqNum = Math.max(highestSeqNum, theirSeqNum);
        boolean ourPriority = theirSeqNum > ourSeqNum || (theirSeqNum == ourSeqNum && j > me);
        logger.trace(logBaseInfo + "--ourSeqNum: " + ourSeqNum + "using: " + using +
                " waiting: " + waiting + " ourPriority: " + ourPriority);
        // defer sending reply msg to jth server
        if (using || (waiting && ourPriority)) {
            replyDeferred[j] = true;
            logger.trace(logBaseInfo + "--defer sending reply from client " + me + " to client " + j);
        }

        // (waiting && !authorization[j] && !ourPriority) means we don't need to consult jth server/process
        // because invoking treatReqMsg function, we don't have permission from j and have already sent consulting request to j
        if (!(using || waiting) || (waiting && !authorization[j] && !ourPriority)) {
            authorization[j] = false;
            String reply = fileId + " " + Constant.REPLY_ME + " " + me;
            putIntoOutboundBlockingQueue(j, reply);
            logger.trace(logBaseInfo + "--reply position 0: send mutex reply from client " +
                    me + " to client " + j + " content: " + reply);
        }

        // consult jth server/process, since before invoking treatReqMsg function, we have permission from j
        // however, we don't have priority, hence permission becomes invalid, we need to consult j immediately
        if (waiting & authorization[j] & !ourPriority) {
            authorization[j] = false;
            String reply = fileId + " " + Constant.REPLY_ME + " " + me;
            putIntoOutboundBlockingQueue(j, reply);
            logger.trace(logBaseInfo + "--reply position 1: send mutex reply from client " +
                    me + " to client " + j + " content: " + reply);

            if (!requestSentSet.contains(j)) {
                requestSet.add(j);
                requestSentSet.add(j);
                String request = fileId + " " + Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + curRequestNum;
                putIntoOutboundBlockingQueue(j, request);
                logger.trace(logBaseInfo + "--request position 1: send mutex request from client " +
                        me + " to client " + j + " content: " + request);
            }
        }
    }

    /**
     * handle reply msg
     * @param otherClientId their client id
     */
    private void treatReplyMsg(int otherClientId) {
        String logBaseInfo = "Request " + curRequestNum + " file " + fileId;
        logger.trace(logBaseInfo + "--get the permission from client " + otherClientId + " to " + me);
        authorization[otherClientId] = true;
        requestSet.remove(otherClientId);
        logger.trace(logBaseInfo + "--further permissions need to receive: " + requestSet.size());
        if (requestSet.size() == 0) {
            logger.trace(logBaseInfo + "--Get all permissions from other client. ourSeqNum: " + ourSeqNum +
                    " curRequestNum: " + curRequestNum);
            waiting = false;
            using = true;
            client.enterCS();
        }
    }

    /**
     * request resource from other client-side server
     * @param requestNum request number
     */
    private void requestResource(int requestNum) {
        waiting = true;
        ourSeqNum = highestSeqNum + 1;
        curRequestNum = requestNum;

        String logBaseInfo = "Request " + requestNum + " file " + fileId;
        for (int id : clientConnMap.keySet()) {
            if (!authorization[id]) {
                logger.trace( logBaseInfo + "--connection needs to ask authorization: " + id);
                if (!requestSentSet.contains(id)) {
                    requestSet.add(id);
                    requestSentSet.add(id);
                    String request = fileId + " " + Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + requestNum;
                    putIntoOutboundBlockingQueue(id, request);
                }
            }
        }

        // if we don't need to ask permission, enter into critical section directly
        if (requestSet.size() == 0) {
            logger.trace(logBaseInfo + "--time when we don't need to ask permission");
            waiting = false;
            using = true;
            client.enterCS();
        }
    }

    /**
     * release resource after operation finishes
     */
    private void releaseResource() {
        using = false;
        for (int i = 0; i < n; i++) {
            if (replyDeferred[i]) {
                replyDeferred[i] = false;
                authorization[i] = false;
                // send reply msg to other server/process
                String reply = fileId + " " + Constant.REPLY_ME + " " + me;
                putIntoOutboundBlockingQueue(i, reply);
                logger.trace("Request " + curRequestNum + " file " + fileId + "--send deferred reply to client " + i + " from " + me +
                        " content: " + reply);
            }
        }

        // clear request sent set
        requestSentSet.clear();
        client.finishCS();
    }

    private void putIntoOutboundBlockingQueue(int target, String msg) {
        try {
            outboundBlockingQueueMap.get(target).put(msg);
            outboundMsgCount++;
            logger.trace("insert outbound msg and prepare to send to client " + target + " msg: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting outbound msg: " + ex.toString());
        }
    }
}
