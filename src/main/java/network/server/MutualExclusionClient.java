package network.server;

import algorithm.CriticalSectionCallback;
import algorithm.RaAlgoWithCrOptimization;
import constant.Constant;
import network.Connection;
import network.client.BaseClient;
import network.handler.MeClientRequestHandler;
import network.handler.RequestHandler;
import network.server.BaseServer;
import util.FileUtil;
import util.SemaUtil;
import util.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: "client side" server for Ricart-Agrawala algorithm
 */
public class MutualExclusionClient extends BaseServer implements CriticalSectionCallback {
    private int clientId;  // client id
    private int clientNum; // quantity of client-side server
    private int serverNum; // quantity of server-side server
    private int fileNum;   // quantity of files
    private int opCount;   // operation count
    // we need to have the mutual exclusion algorithm object for each file
    private List<RaAlgoWithCrOptimization> meAlgoList = new ArrayList<>();
    private Map<Integer, Connection> clientConnMap = new ConcurrentHashMap<>();
    private Map<Integer, Connection> serverConnMap = new ConcurrentHashMap<>();
    private String[] operations = {Constant.REQ_SERVER_ENQUIRY, Constant.REQ_SERVER_READ, Constant.REQ_SERVER_WRITE};
    private int curOpCount = 0;
    private String opType;
    private int fileId;
    private RaAlgoWithCrOptimization curMEAlgo;
    private Semaphore writeCountMutex = new Semaphore(1);
    private volatile int writeResponseCount;

    public MutualExclusionClient(int clientId, int clientNum, int serverNum, int fileNum, int opCount) {
        super(Constant.BASE_CLIENT_PORT + clientId, Constant.CLIENT);
        this.clientId = clientId;
        this.clientNum = clientNum;
        this.serverNum = serverNum;
        this.fileNum = fileNum;
        this.opCount = opCount;
    }

    @Override
    protected void startHandler(Connection connection, String clientType, int otherClientId) {
//        switch (clientType) {
//            case Constant.SERVER: {
//                System.out.println("record connection from server" + otherClientId);
//                if (connection == null) {
//                    System.out.println("connection is null");
//                }
//                serverConnMap.put(otherClientId, connection);
//                break;
//            }
//            case Constant.CLIENT: {
//                System.out.println("record connection from client" + otherClientId);
//                if (connection == null) {
//                    System.out.println("connection is null");
//                }
//                clientConnMap.put(otherClientId, connection);
//                break;
//            }
//        }
        MeClientRequestHandler handler = new MeClientRequestHandler(connection, clientId, clientType + otherClientId, meAlgoList, this);
        handler.start();
    }


    @Override
    public void enterCS() {
        System.out.println("Request " + curOpCount + " enter into critical section");
        sendReq(opType);
    }

    @Override
    public void finishCS() {
        System.out.println("Request " + curOpCount + " finished");
        executeOperation();
    }

    @Override
    public void checkWriteComplete() {
        SemaUtil.wait(writeCountMutex);
        writeResponseCount++;
        if (writeResponseCount == serverNum) {
            // reset count
            writeResponseCount = 0;
            SemaUtil.signal(writeCountMutex);
            System.out.println("Request " + curOpCount + " write to all servers finished, start releasing resource");
            // write operation to all replicas succeed, release resource
            curMEAlgo.releaseResource();
        } else {
            SemaUtil.signal(writeCountMutex);
        }
    }

    /**
     * init all connections with server-side servers and client-side servers
     * note: only connect to client-side servers which have larger clientId
     */
    public void initConnection() {
        Set<String> machineInfo = FileUtil.readAllLines(System.getProperty("user.dir") + "/" + Constant.MACHINE_INFO_FILE_PATH);
        for (String info : machineInfo) {
            String[] split1 = info.split(",");
            String[] split2 = split1[1].split(":");
            int port = Integer.parseInt(split2[1]);
            if (split1[0].startsWith(Constant.SERVER)) {
                BaseClient client = new BaseClient(split2[0], port, clientId, Constant.CLIENT);
                String threadName = Constant.SERVER + (port - Constant.BASE_SERVER_PORT);
                MeClientRequestHandler handler = new MeClientRequestHandler(client.getConnection(), clientId, threadName, meAlgoList, this);
                handler.start();
                serverConnMap.put(port - Constant.BASE_SERVER_PORT, client.getConnection());
            } else if (!split1[0].startsWith(Constant.CLIENT + clientId)) {
                // TODO only connect to bigger "port" number, avoid duplicate connection
                // TODO current implementation is not a good way.
                BaseClient client = new BaseClient(split2[0], port, clientId, Constant.CLIENT);
                String threadName = Constant.CLIENT + (port - Constant.BASE_CLIENT_PORT);
                MeClientRequestHandler handler = new MeClientRequestHandler(client.getConnection(), clientId, threadName, meAlgoList, this);
                handler.start();
                clientConnMap.put(port - Constant.BASE_CLIENT_PORT, client.getConnection());
            }
        }
    }

    /**
     * close all connections with server-side servers and client-side servers
     */
    public void closeConnection() {
        System.out.println("close all connections");
        try {
            for (Connection conn : serverConnMap.values()) {
                conn.writeUTF(Constant.EXIT);
                conn.closeConnection();
            }
            for (Connection conn : clientConnMap.values()) {
                conn.writeUTF(Constant.EXIT);
                conn.closeConnection();
            }
        } catch (EOFException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * init mutual exclusion algorithm list
     * each file has its own mutual exclusion algorithm object
     */
    public void initMEList() {
        for (int i = 0; i < fileNum; i++) {
            meAlgoList.add(new RaAlgoWithCrOptimization(clientId, clientNum, i, clientConnMap, this));
        }
    }

    /**
     * clear mutual exclusion list
     */
    public void clearMEList() {
        meAlgoList.clear();
    }

    /**
     * execute operations, including read, write and enquiry
     */
    public void executeOperation() {
        if (curOpCount >= opCount) {
            curOpCount = 0;
            return;
        }
        int opId = Util.genRandom(3);
        opType = operations[opId];
        fileId = Util.genRandom(fileNum);
        curOpCount++;
        System.out.println("Request " + curOpCount + ": " + opType + " file" + fileId);

        // for ENQUIRY operation, no need to request resource
        if (opType.equals(Constant.REQ_SERVER_ENQUIRY)) {
            sendReq(Constant.REQ_SERVER_ENQUIRY);
            return;
        }

        curMEAlgo = meAlgoList.get(fileId);
        curMEAlgo.requestResource(opType, curOpCount);
    }

    /**
     * send different operation request
     * @param type operation type
     */
    private void sendReq(String type) {
        switch (type) {
            case Constant.REQ_SERVER_ENQUIRY: {
                int id = Util.genRandom(serverNum);
                Connection connection = serverConnMap.get(id);
                String request = Constant.REQ_SERVER_ENQUIRY + " fromClient" + clientId + " requestNum" + curOpCount;
                connection.writeUTF(request);
                System.out.println(request + "--send to server" + id);
                break;
            }
            case Constant.REQ_SERVER_READ: {
                System.out.println("Request " + curOpCount + " " + Constant.REQ_SERVER_READ);
                int id = Util.genRandom(serverNum);
                Connection connection = serverConnMap.get(id);
                String request = Constant.REQ_SERVER_READ + " " + fileId + " fromClient" + clientId + " requestNum" + curOpCount;
                connection.writeUTF(request);
                System.out.println("Request " + curOpCount + " " + Constant.REQ_SERVER_READ +
                        " -- content: " + request + ". send to server" + id);
                break;
            }
            case Constant.REQ_SERVER_WRITE: {
                System.out.println("Request " + curOpCount + " " + Constant.REQ_SERVER_WRITE);
                long timestamp = System.currentTimeMillis();
                for (int i = 0; i < serverNum; i++) {
                    String content = "<" + clientId + "," + timestamp + ">";
                    String request = Constant.REQ_SERVER_WRITE + " " + fileId + " " + content + " fromClient" + clientId + " requestNum" + curOpCount;
                    serverConnMap.get(i).writeUTF(request);
                    System.out.println("Request " + curOpCount + " " + Constant.REQ_SERVER_WRITE +
                            " -- content: " + request + ". send to server" + i);
                }
                break;
            }
            default:
                break;

        }
    }

}
