package network.handler;

import constant.Constant;
import network.Connection;

import java.io.EOFException;
import java.io.IOException;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: Base request handler
 */
public abstract class RequestHandler extends Thread {
    protected Connection connection;
    protected int id;

    public RequestHandler(Connection connection, int id, String name) {
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
                    break;
                } catch (IOException ex) {
                    ex.printStackTrace();
                    break;
                }
                break;
            }

            handleMsg(received);
        }

    }

    /**
     * handle incoming msg
     * @param msg message
     */
    protected abstract void handleMsg(String msg);
}
