package network.server;

import constant.Constant;
import network.Connection;
import network.handler.inbound.ServerInboundMsgHandler;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: "server side" server for Ricart-Agrawala algorithm
 */
public class MutualExclusionServer extends BaseServer {
    private int serverId;

    public MutualExclusionServer(int serverId) {
        super(Constant.BASE_SERVER_PORT + serverId, Constant.SERVER);
        this.serverId = serverId;
    }

    @Override
    protected void startHandler(Connection connection, String clientType, int otherClientId) {
        ServerInboundMsgHandler handler = new ServerInboundMsgHandler(connection, serverId, clientType + otherClientId);
        handler.start();
    }
}
