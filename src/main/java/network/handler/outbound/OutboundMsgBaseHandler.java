package network.handler.outbound;

import constant.Constant;
import network.Connection;

import java.io.EOFException;
import java.io.IOException;

/**
 * Author: JeffinBao
 * Date: 2019-10-31
 * Usage: Base outbound message handler
 */
public abstract class OutboundMsgBaseHandler extends Thread {
    protected Connection connection;
    protected int id;

    public OutboundMsgBaseHandler(Connection connection, int id, String name) {
        super(name);
        this.connection = connection;
        this.id = id;
    }

    @Override
    public void run() {
        while (true) {
            String outboundMsg = retrieveOutboundMsg();
            if (outboundMsg.equals(Constant.EXIT) ||
                    outboundMsg.equals(Constant.EMPTY_STRING)) {
                try {
                    connection.writeUTF(outboundMsg);
                    connection.closeConnection();
                } catch (EOFException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                break;
            }

            connection.writeUTF(outboundMsg);
        }
    }

    protected abstract String retrieveOutboundMsg();
}
