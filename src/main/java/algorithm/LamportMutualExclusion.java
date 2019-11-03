package algorithm;

import constant.Constant;
import network.Connection;
import network.OutboundMessage;
import network.server.MutualExclusionClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-10-12
 * Usage: Lamport Distributed Mutual Exclusion algorithm with optimization
 */
public class LamportMutualExclusion {
    private static Logger logger = null;
    // me is the server/process id
    // ourSeqNum is the sequence number of current server/process
    // highestSeqNum is the highest sequence number in the system
    private int me;
    private int fileId;
    private Map<Integer, Connection> clientConnMap;
    private MutualExclusionClient client;
    private LinkedBlockingQueue<String> inboundMsgBlockingQueue;
    private LinkedBlockingQueue<OutboundMessage> outboundMsgBlockingQueue;
    private boolean closeHandler;
    private volatile boolean using;
    private volatile int highestSeqNum;
    private Request curRequest;
    private List<Integer> requestDesIdList = new ArrayList<>();
    private PriorityQueue<Request> requestPQ = new PriorityQueue<>();

    public LamportMutualExclusion(int me, int fileId,
                                  Map<Integer, Connection> clientConnMap,
                                  MutualExclusionClient client,
                                  LinkedBlockingQueue<String> inboundMsgBlockingQueue,
                                  LinkedBlockingQueue<OutboundMessage> outboundMsgBlockingQueue) {
        this.me = me;
        this.fileId = fileId;
        this.clientConnMap = clientConnMap;
        this.client = client;
        this.inboundMsgBlockingQueue = inboundMsgBlockingQueue;
        this.outboundMsgBlockingQueue = outboundMsgBlockingQueue;
        logger = LogManager.getLogger("client" + me + "_logger");
        new MessageHandler().start();
    }

    private class MessageHandler extends Thread {
        @Override
        public void run() {
            while (!closeHandler) {
                try {
                    String message = inboundMsgBlockingQueue.take();
                    logger.trace("handle inbound msg: " + message);
                    handleMsg(message);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                    logger.trace("failed handling inbound msg: " + ex.toString());
                    break;
                }
            }
        }
    }

    /**
     * close handler
     */
    public void tearDown() {
        closeHandler = true;
    }

    /**
     * handle inbound message retrieved from blocking queue
     * @param msg inbound message
     */
    private void handleMsg(String msg) {
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
                String[] split1 = split[1].split(" ");
                requestResource(split1[0], Integer.parseInt(split1[1]));
                break;
            }
        }
    }

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
        OutboundMessage outboundMessage = new OutboundMessage(j, reply);
        putIntoBlockingQueue(outboundMessage);
    }

    private void treatReplyMsg(int otherClientId, int otherSeqNum) {
        String logBaseInfo = "Request " + curRequest.getRequestNum() + " file " + fileId;
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        logger.trace(logBaseInfo + "--reply from client " + otherClientId + " to " + me + " otherSeqNum: " + otherSeqNum +
                " highestSeqNum: " + highestSeqNum);
        checkIncomingMsgSeqNum(otherClientId, otherSeqNum);
    }

    private void treatReleaseMsg(int otherClientId, int otherSeqNum) {
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        logger.trace("release msg from client " + otherClientId + " to " + me +
                " otherSeqNum: " + "highestSeqNum: " + highestSeqNum);
        requestPQ.poll();
        checkIncomingMsgSeqNum(otherClientId, otherSeqNum);
    }

    private void requestResource(String opType, int requestNum) {
        highestSeqNum++;
        String reqMsg = fileId + " " + Constant.REQ_ME + " " + highestSeqNum + " " + me + " " + requestNum;
        logger.trace("Request " + requestNum + "--reqMsg: " + reqMsg);
        for (Integer clientId : clientConnMap.keySet()) {
            OutboundMessage outboundMessage = new OutboundMessage(clientId, reqMsg);
            putIntoBlockingQueue(outboundMessage);
            requestDesIdList.add(clientId);
        }
        curRequest = new Request(requestNum, highestSeqNum, me);
        requestPQ.offer(curRequest);
    }

    private void releaseResource() {
        using = false;
        highestSeqNum++;
        String releaseMsg = fileId + " " + Constant.RELEASE_ME + " " + highestSeqNum + " " + me;
        logger.trace("Request " + curRequest.getRequestNum() + "--releaseMsg: " + releaseMsg);
        for (Integer clientId : clientConnMap.keySet()) {
            OutboundMessage outboundMessage = new OutboundMessage(clientId, releaseMsg);
            putIntoBlockingQueue(outboundMessage);
        }
        requestPQ.poll();
        curRequest = null;
        client.finishCS();
    }

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

    private void putIntoBlockingQueue(OutboundMessage msg) {
        try {
            outboundMsgBlockingQueue.put(msg);
            logger.trace("insert outbound msg to blocking queue: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting outbound msg: " + ex.toString());
        }
    }
}
