package network.server;

import constant.Constant;
import network.Connection;
import util.FileUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Author: JeffinBao
 * Date: 2019-09-07
 * Usage: Base server which can be inherited for use
 */
public abstract class BaseServer {
    private int port;
    private String serverType;
    private String machineInfoPath = System.getProperty("user.dir") + "/" + Constant.MACHINE_INFO_FILE_PATH;

    public BaseServer(int port, String serverType) {
        this.port = port;
        this.serverType = serverType;

        // start a thread to initialize as a server
        // waiting to receive requests from other clients
        (new Thread() {
            @Override
            public void run() {
                BaseServer.this.init();
            }
        }).start();
    }

    /**
     * init a server
     */
    private void init() {
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            recordMachineInfo();

            while (true) {
                Socket socket = server.accept();
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                Connection connection = new Connection(socket, dis, dos);

                String firstMsg = connection.readUTF();
                String[] split = firstMsg.split(",");
                System.out.println("A new client is connected: " + socket);
                connection.writeUTF("Connected to " + port);

                System.out.println("Assign new thread for this client");
                startHandler(connection, split[0], Integer.parseInt(split[1]));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Record machine information after a server is up
     */
    private void recordMachineInfo() {
        try {
            // write machine info into file, like a local dns
            String hostName = InetAddress.getLocalHost().getHostName();
            String content = "";
            switch (serverType) {
                case Constant.SERVER: {
                    content = "server" + (port - Constant.BASE_SERVER_PORT) + "," + hostName + ":" + port;
                    break;
                }
                case Constant.CLIENT: {
                    content = "client" + (port - Constant.BASE_CLIENT_PORT) + "," + hostName + ":" + port;
                }
            }
            FileUtil.write(machineInfoPath, content);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * start handler for handling received msg
     * @param connection socket connection
     * @param clientType client type: server or client
     * @param otherClientId the id of client which connects to the server
     */
    protected abstract void startHandler(Connection connection, String clientType, int otherClientId);
}
