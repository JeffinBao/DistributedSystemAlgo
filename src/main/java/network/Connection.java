package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

/**
 * Author: JeffinBao
 * Date: 2019-09-11
 * Usage: Connection between client and server
 */
public class Connection {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    public Connection(Socket socket, DataInputStream dis, DataOutputStream dos) {
        this.socket = socket;
        this.dis = dis;
        this.dos = dos;
    }

    /**
     * send msg to server and wait for response
     * @param msg message to be sent
     */
    public void writeUTF(String msg) {
        try {
            dos.writeUTF(msg);
            dos.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * receive response from server
     * @return string type message
     */
    public String readUTF() throws EOFException, IOException {
        return dis.readUTF();
    }

    /**
     * get socket
     * @return socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * close connection
     */
    public void closeConnection() throws EOFException, IOException {
        System.out.println("Closing client side connection: " + socket);
        dis.close();
        dos.close();
        socket.close();
        System.out.println("Connection closed");
    }
}
