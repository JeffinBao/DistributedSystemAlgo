package network.handler;

import algorithm.CriticalSectionCallback;
import algorithm.RaAlgoWithCrOptimization;
import constant.Constant;
import network.Connection;
import network.server.MutualExclusionClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Author: JeffinBao
 * Date: 2019-09-18
 * Usage: Client-side server request handler
 */
public class MeClientRequestHandler extends RequestHandler {
    private static Logger logger = null;
    private List<LinkedBlockingQueue<String>> blockingQueueList;
    private MutualExclusionClient client;

    public MeClientRequestHandler(Connection connection, int id, String name,
                                  List<LinkedBlockingQueue<String>> blockingQueueList,
                                  MutualExclusionClient client) {
        super(connection, id, name);
        this.blockingQueueList = blockingQueueList;
        this.client = client;
        logger = LogManager.getLogger("client" + id + "_logger");
    }

    @Override
    protected void handleMsg(String msg) {
        // TODO we need to add a queue to store the incoming msg,
        // it is possible that REQUEST and REPLY are coming at the same time.
        String[] split = msg.split(" ", 2);
        switch (split[0]) {
            case Constant.REPLY_SERVER_ENQUIRY: {
                // Format: Constant.REPLY_SERVER_ENQUIRY fromClientX requestNumX response
                client.finishCS();
                break;
            }
            case Constant.REPLY_SERVER_WRITE: {
                // Format: Constant.REPLY_SERVER_WRITE + " " + response
                client.checkWriteComplete();
                break;
            }
            default: {
                LinkedBlockingQueue<String> inboundMsgBlockingQueue = blockingQueueList.get(Integer.parseInt(split[0]));
                try {
                    inboundMsgBlockingQueue.put(split[1]);
                    logger.trace("received inbound msg: " + msg);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                    logger.trace("failed inserting inbound msg: " + ex.toString());
                }
                break;
            }
        }
    }
}
