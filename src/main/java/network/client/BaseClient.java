package network.client;

import network.Connection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: Base client class, used to initialize a client connection to server
 */
public class BaseClient {
    private Connection connection;
    private String hostName;
    private int port; // server port
    private int ourOwnClientId; // our own client id
    private String clientType;

    public BaseClient(String hostName, int port, int ourOwnClientId, String clientType) {
        this.hostName = hostName;
        this.port = port;
        this.ourOwnClientId = ourOwnClientId;
        this.clientType = clientType;
        init();
    }

    /**
     * init a client connection to server
     */
    private void init() {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostName);

            Socket socket = new Socket(inetAddress, port);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            connection = new Connection(socket, dis, dos);
            System.out.println("Connecting to " + hostName + ":" + port);

            connection.writeUTF(clientType + "," + ourOwnClientId);
            // first return msg is "Connected to xxx"
            String receive = connection.readUTF();
            System.out.println(receive);
            System.out.println("Client socket info: " + socket);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * get client connection
     * @return connection
     */
    public Connection getConnection() {
        return connection;
    }
}
