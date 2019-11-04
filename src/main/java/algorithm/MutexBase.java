package algorithm;

import network.Connection;
import network.server.MutualExclusionClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-11-03
 * Usage: Distributed mutual exclusion base class, will be inherited
 *        by various algorithms
 */
public abstract class MutexBase {
    protected static Logger logger = null;
    protected int me;
    protected int fileId;
    protected Map<Integer, Connection> clientConnMap;
    protected MutualExclusionClient client;
    private LinkedBlockingQueue<String> inboundMsgBlockingQueue;
    protected Map<Integer, LinkedBlockingQueue<String>> outboundBlockingQueueMap;
    private boolean closeHandler;
    protected int inboundMsgCount;
    protected int outboundMsgCount;

    public MutexBase(int me, int fileId,
                     Map<Integer, Connection> clientConnMap,
                     MutualExclusionClient client,
                     LinkedBlockingQueue<String> inboundMsgBlockingQueue,
                     Map<Integer, LinkedBlockingQueue<String>> outboundBlockingQueueMap) {
        this.me = me;
        this.fileId = fileId;
        this.clientConnMap = clientConnMap;
        this.client = client;
        this.inboundMsgBlockingQueue = inboundMsgBlockingQueue;
        this.outboundBlockingQueueMap = outboundBlockingQueueMap;
        logger = LogManager.getLogger("client" + me + "_logger");
        new MessageHandler().start();
    }

    private class MessageHandler extends Thread {
        @Override
        public void run() {
            while (!closeHandler) {
                try {
                    String message = inboundMsgBlockingQueue.take();
                    inboundMsgCount++;
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
     * get inbound message count
     * @return message count
     */
    public int getInboundMsgCount() {
        return inboundMsgCount;
    }

    /**
     * get outbound message count
     * @return message count
     */
    public int getOutboundMsgCount() {
        return outboundMsgCount;
    }

    /**
     * handle inbound message in various mutex algorithm
     * @param msg message
     */
    protected abstract void handleMsg(String msg);
}
