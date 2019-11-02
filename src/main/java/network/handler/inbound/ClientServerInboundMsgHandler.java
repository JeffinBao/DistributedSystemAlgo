package network.handler.inbound;

import constant.Constant;
import network.Connection;
import network.server.MutualExclusionClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Author: JeffinBao
 * Date: 2019-10-31
 * Usage: Inbound message handler between client and server
 */
public class ClientServerInboundMsgHandler extends InboundMsgBaseHandler {
    private static Logger logger = null;
    private MutualExclusionClient client;

    public ClientServerInboundMsgHandler(Connection connection, int id, String name,
                                         MutualExclusionClient client) {
        super(connection, id, name);
        this.client = client;
        logger = LogManager.getLogger("client" + id + "_logger");
    }

    @Override
    protected void handleMsg(String msg) {
        logger.trace("Inbound message: " + msg);
        String[] split = msg.split(" ", 2);
        switch (split[0]) {
            case Constant.REPLY_SERVER_ENQUIRY: {
                // Format: Constant.REPLY_SERVER_ENQUIRY fromClientX requestNumX response
                client.finishCS();
                break;
            }
            case Constant.REPLY_SERVER_READ: {
                // Format: "Constant.REQ_SERVER_READ fileId fromClientX requestNumX"
                client.finishReadOperation();
                break;
            }
            case Constant.REPLY_SERVER_WRITE: {
                // Format: Constant.REPLY_SERVER_WRITE + " " + response
                client.checkWriteFinish();
                break;
            }
        }
    }
}
