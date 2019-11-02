package network.handler.inbound;

import network.Connection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-10-31
 * Usage: Inbound message handler between clients
 */
public class ClientClientInboundMsgHandler extends InboundMsgBaseHandler {
    private static Logger logger = null;
    private List<LinkedBlockingQueue<String>> blockingQueueList;

    public ClientClientInboundMsgHandler(Connection connection, int id, String name,
                                         List<LinkedBlockingQueue<String>> blockingQueueList) {
        super(connection, id, name);
        this.blockingQueueList = blockingQueueList;
        logger = LogManager.getLogger("client" + id + "_logger");
    }

    @Override
    protected void handleMsg(String msg) {
        String[] split = msg.split(" ", 2);
        LinkedBlockingQueue<String> inboundMsgBlockingQueue = blockingQueueList.get(Integer.parseInt(split[0]));
        try {
            inboundMsgBlockingQueue.put(split[1]);
            logger.trace("received inbound msg: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting inbound msg: " + ex.toString());
        }
    }
}
