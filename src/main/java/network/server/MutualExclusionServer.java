package network.server;

import constant.Constant;
import network.Connection;
import network.handler.MeServerRequestHandler;
import network.handler.RequestHandler;
import network.server.BaseServer;
import org.omg.PortableServer.POA;
import util.FileUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

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
        MeServerRequestHandler handler = new MeServerRequestHandler(connection, serverId, clientType + otherClientId);
        handler.start();
    }
}
