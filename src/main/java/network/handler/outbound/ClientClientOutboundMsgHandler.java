package network.handler.outbound;

import network.Connection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-10-31
 * Usage: Outbound message handler between clients
 */
public class ClientClientOutboundMsgHandler extends OutboundMsgBaseHandler {
    private static Logger logger = null;
    private LinkedBlockingQueue<String> blockingQueue;

    public ClientClientOutboundMsgHandler(Connection connection, int id, String name,
                                          LinkedBlockingQueue<String> blockingQueue) {
        super(connection, id, name);
        this.blockingQueue = blockingQueue;
        logger = LogManager.getLogger("client" + id + "_logger");
    }

    @Override
    protected String retrieveOutboundMsg() {
        String outboundMessage = "";
        try {
            outboundMessage = blockingQueue.take();
            logger.trace("sent outboundMsg: " + outboundMessage);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed sending outboundMsg: " + ex.toString());
        }

        return outboundMessage;
    }
}
