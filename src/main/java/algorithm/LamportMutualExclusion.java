package algorithm;

import constant.Constant;
import network.Connection;
import network.OutboundMessage;
import network.server.MutualExclusionClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import util.SemaUtil;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Author: JeffinBao
 * Date: 2019-10-12
 * Usage: Lamport Distributed Mutual Exclusion algorithm with optimization
 */
public class LamportMutualExclusion {
    private static Logger logger = null;
    private PriorityQueue<Request> requestPQ = new PriorityQueue<>();
    // me is the server/process id
    // n is the number of server/process in the system
    // ourSeqNum is the sequence number of current server/process
    // highestSeqNum is the highest sequence number in the system
    private int me, n;
    private volatile int highestSeqNum;
    private volatile boolean using = false;
    private Map<Integer, Connection> clientConnMap;
    // distinguish different file, each file should have a RAAlgoWithCROptimization object
    private int fileId;
    private Semaphore mutex = new Semaphore(1);
    private List<Integer> requestDesIdList = new ArrayList<>();
    private Request curRequest;
    private MutualExclusionClient client;
    private LinkedBlockingQueue<String> inboundMsgBlockingQueue;
    private LinkedBlockingQueue<OutboundMessage> outboundMsgBlockingQueue;
    private boolean closeHandler;

    public LamportMutualExclusion(int me, int n, int fileId,
                                  Map<Integer, Connection> clientConnMap,
                                  MutualExclusionClient client,
                                  LinkedBlockingQueue<String> inboundMsgBlockingQueue,
                                  LinkedBlockingQueue<OutboundMessage> outboundMsgBlockingQueue) {
        this.me = me;
        this.n = n;
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

    public void tearDown() {
        closeHandler = true;
    }

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
            case Constant.REPLY_SERVER_READ:
            case Constant.INIT_RELEASE: {
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
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        Request otherReq = new Request(otherReqNum, otherSeqNum, j);
        requestPQ.offer(otherReq);
        checkIncomingMsgSeqNum(j, otherSeqNum);
        // optimization: if the process has already sent a request with higher timestamp
        // we can suppress a reply
        if (curRequest != null && curRequest.getSequenceNum() > otherSeqNum) {
            return;
        }
        String reply = fileId + " " + Constant.REPLY_ME + " " + highestSeqNum + " " + me;
        OutboundMessage outboundMessage = new OutboundMessage(j, reply);
        putIntoBlockingQueue(outboundMessage);
    }

    private void treatReplyMsg(int otherClientId, int otherSeqNum) {
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        checkIncomingMsgSeqNum(otherClientId, otherSeqNum);
    }

    private void treatReleaseMsg(int otherClientId, int otherSeqNum) {
        highestSeqNum = Math.max(highestSeqNum, otherSeqNum) + 1;
        requestPQ.poll();
        checkIncomingMsgSeqNum(otherClientId, otherSeqNum);
    }

    private void requestResource(String opType, int requestNum) {
        highestSeqNum++;
        String reqMsg = fileId + " " + Constant.REQ_ME + " " + highestSeqNum + " " + me + " " + requestNum;
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
        curRequest = null;
        highestSeqNum++;
        String releaseMsg = fileId + " " + Constant.RELEASE_ME + " " + highestSeqNum + " " + me;
        for (Integer clientId : clientConnMap.keySet()) {
            OutboundMessage outboundMessage = new OutboundMessage(clientId, releaseMsg);
            putIntoBlockingQueue(outboundMessage);
        }
        requestPQ.poll();
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
