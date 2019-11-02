package network.handler.inbound;

import constant.Constant;
import network.Connection;

import java.io.EOFException;
import java.io.IOException;

/**
 * Author: JeffinBao
 * Date: 2019-10-31
 * Usage: Base inbound message handler
 */
public abstract class InboundMsgBaseHandler extends Thread {
    protected Connection connection;
    protected int id;

    InboundMsgBaseHandler(Connection connection, int id, String name) {
        super(name);
        this.connection = connection;
        this.id = id;
    }

    @Override
    public void run() {
        String received;
        while (true) {
            try {
                received = connection.readUTF();
            } catch (EOFException ex) {
                ex.printStackTrace();
                break;
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }

            if (received.startsWith(Constant.EXIT)) {
                // close socket connection
                System.out.println("Client " + connection.getSocket() + " sends exit...");
                try {
                    connection.closeConnection();
                } catch (EOFException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                break;
            }

            handleMsg(received);
        }

    }

    /**
     * handle inbound msg
     * @param msg message
     */
    protected abstract void handleMsg(String msg);
}
