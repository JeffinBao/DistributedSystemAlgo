package algorithm;

import constant.Constant;
import network.Connection;
import network.server.MutualExclusionClient;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-10-12
 * Usage: Lamport Distributed Mutual Exclusion algorithm with optimization
 */
public class LamportMutualExclusion extends MutexBase {
    // me is the server/process id
    // highestSeqNum is the highest sequence number in the system
    private volatile boolean using;
    private volatile int highestSeqNum;
    private Request curRequest;
    private List<Integer> requestDesIdList = new ArrayList<>();
    private PriorityQueue<Request> requestPQ = new PriorityQueue<>();

    public LamportMutualExclusion(int me, int fileId,
                                  Map<Integer, Connection> clientConnMap,
                                  MutualExclusionClient client,
                                  LinkedBlockingQueue<String> inboundMsgBlockingQueue,
                                  Map<Integer, LinkedBlockingQueue<String>> outboundBlockingQueueMap) {
        super(me, fileId, clientConnMap, client, inboundMsgBlockingQueue, outboundBlockingQueueMap);
    }

    /**
     * handle inbound message retrieved from blocking queue
     * @param msg inbound message
     */
    protected void handleMsg(String msg) {
        String[] split = msg.split(" ", 2);
        switch (split[0]) {
            case Constant.REQ_ME: {
                // Format: fileId + Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + requestNum
                String[] split1 = split[1].split(" ");
                int otherSeqNum = Integer.parseInt(split1[0]);
                int otherClientId = Integer.parseInt(split1[1]);
                int otherRequestNum = Integer.parseInt(split1[2]);
                treatRequestMsg(otherSeqNum, otherClientId, otherRequestNum);
                break;
            }
            case Constant.REPLY_ME: {
                // Format: fileId + " " + Constant.REPLY_ME + " " + timestamp + " " + me
                String[] split1 = split[1].split(" ");
                int otherSeqNum = Integer.parseInt(split1[0]);
                int otherClientId = Integer.parseInt(split1[1]);
                treatReplyMsg(otherClientId, otherSeqNum);
                break;
            }
            case Constant.RELEASE_ME: {
                // Format:  fileId + " " + Constant.RELEASE_ME + " " + timestamp + " " + me
                String[] split1 = split[1].split(" ");
                int otherSeqNum = Integer.parseInt(split1[0]);
                int otherClientId = Integer.parseInt(split1[1]);
                treatReleaseMsg(otherClientId, otherSeqNum);
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
     * handle request message from other clients
     * @param otherSeqNum other client's sequence number
     * @param j other client's id
     * @param otherReqNum other client's request number
     */
    private void treatRequestMsg(int otherSeqNum, int j, int otherReqNum) {
        String logBaseInfo = "otherRequestNum: " + otherReqNum + " file " + fileId;
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        logger.trace(logBaseInfo + "--otherSeqNum: " + otherSeqNum + " highestSeqNum: " + highestSeqNum);
        Request otherReq = new Request(otherReqNum, otherSeqNum, j);
        requestPQ.offer(otherReq);
        checkIncomingMsgSeqNum(j, otherSeqNum);
        // optimization: if the process has already sent a request with higher timestamp
        // we can suppress a reply
        if (curRequest != null && curRequest.getSequenceNum() > otherSeqNum) {
            return;
        }
        String reply = fileId + " " + Constant.REPLY_ME + " " + highestSeqNum + " " + me;
        logger.trace(logBaseInfo + "--reply from client: " + me + " to: " + j + " content: " + reply);
        putIntoOutboundBlockingQueue(j, reply);
    }

    /**
     * handle reply message from other clients
     * @param otherClientId other client's id
     * @param otherSeqNum other client's sequence number
     */
    private void treatReplyMsg(int otherClientId, int otherSeqNum) {
        String logBaseInfo = "file " + fileId;
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        logger.trace(logBaseInfo + "--reply from client " + otherClientId + " to " + me + " otherSeqNum: " + otherSeqNum +
                " highestSeqNum: " + highestSeqNum);
        checkIncomingMsgSeqNum(otherClientId, otherSeqNum);
    }

    /**
     * handle release message from other clients
     * @param otherClientId other client's id
     * @param otherSeqNum other sequence number
     */
    private void treatReleaseMsg(int otherClientId, int otherSeqNum) {
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        logger.trace("file " + fileId + "--release msg from client " + otherClientId + " to " + me +
                " otherSeqNum: " + "highestSeqNum: " + highestSeqNum);
        requestPQ.poll();
        checkIncomingMsgSeqNum(otherClientId, otherSeqNum);
    }

    /**
     * request resource from other clients
     * @param requestNum current request number
     */
    private void requestResource(int requestNum) {
        highestSeqNum++;
        String reqMsg = fileId + " " + Constant.REQ_ME + " " + highestSeqNum + " " + me + " " + requestNum;
        logger.trace("Request " + requestNum + "--reqMsg: " + reqMsg);
        for (int clientId : clientConnMap.keySet()) {
            putIntoOutboundBlockingQueue(clientId, reqMsg);
            requestDesIdList.add(clientId);
        }
        curRequest = new Request(requestNum, highestSeqNum, me);
        requestPQ.offer(curRequest);
    }

    /**
     * release resource
     */
    private void releaseResource() {
        using = false;
        highestSeqNum++;
        String releaseMsg = fileId + " " + Constant.RELEASE_ME + " " + highestSeqNum + " " + me;
        logger.trace("Request " + curRequest.getRequestNum() + "--releaseMsg: " + releaseMsg);
        for (int clientId : clientConnMap.keySet()) {
            putIntoOutboundBlockingQueue(clientId, releaseMsg);
        }
        requestPQ.poll();
        curRequest = null;
        client.finishCS();
    }

    /**
     * check inbound message sequence num,
     * whether current request can enter critical section
     * @param otherClientId other client's id
     * @param otherSeqNum other client's sequence number
     */
    private void checkIncomingMsgSeqNum(int otherClientId, int otherSeqNum) {
        // receiving msg with higher timestamp than current request
        if (curRequest != null && otherSeqNum > curRequest.getSequenceNum()) {
            requestDesIdList.remove(Integer.valueOf(otherClientId));
            if (requestPQ.size() != 0) {
                Request topReq = requestPQ.peek();
                // receiving msg from all sites and find current request at the top of its request queue
                if (requestDesIdList.size() == 0 && !using &&
                        topReq.getRequestOriginId() == me &&
                        topReq.getSequenceNum() == curRequest.getSequenceNum()) {
                    logger.trace("Request " + curRequest.getRequestNum() + " file " + fileId + "--receive msg from all sites and " +
                            "find current request at the top of its request queue");
                    using = true;
                    client.enterCS();
                }
            }
        }
    }

    /**
     * insert outbound message to client-client handler's blocking queue
     * @param target target client id
     * @param msg outbound message
     */
    private void putIntoOutboundBlockingQueue(int target, String msg) {
        try {
            outboundBlockingQueueMap.get(target).put(msg);
            logger.trace("insert outbound msg and prepare to send to client " + target + " msg: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting outbound msg: " + ex.toString());
        }
    }
}
