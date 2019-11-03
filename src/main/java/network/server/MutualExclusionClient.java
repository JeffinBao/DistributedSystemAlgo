package network.server;

import algorithm.RaAlgoWithCrOptimization;
import constant.Constant;
import network.Connection;
import network.client.BaseClient;
import network.handler.inbound.ClientClientInboundMsgHandler;
import network.handler.inbound.ClientServerInboundMsgHandler;
import network.handler.outbound.ClientClientOutboundMsgHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import util.FileUtil;
import util.SemaUtil;
import util.Util;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: "client side" server for Ricart-Agrawala algorithm
 */
public class MutualExclusionClient extends BaseServer {
    private static Logger logger = null;
    private int clientId;  // client id
    private int clientNum; // quantity of client-side server
    private int serverNum; // quantity of server-side server
    private int fileNum;   // quantity of files
    private int opCount;   // operation count
    private int curOpCount = 0; // current operation count
    private String opType; // current operation type
    private int fileId;    // current operation's targeting fileId
    private String[] operations = {Constant.REQ_SERVER_ENQUIRY, Constant.REQ_SERVER_READ, Constant.REQ_SERVER_WRITE};
    // we need to have the mutual exclusion algorithm object for each file
    private List<RaAlgoWithCrOptimization> meAlgoList = new ArrayList<>();
    private Map<Integer, Connection> clientConnMap = new ConcurrentHashMap<>();
    private Map<Integer, Connection> serverConnMap = new ConcurrentHashMap<>();
    private LinkedBlockingQueue<String> curInboundMsgBlockingQueue;
    private List<LinkedBlockingQueue<String>> blockingQueueList = new ArrayList<>();
    private Map<Integer, LinkedBlockingQueue<String>> outboundBlockingQueueMap = new HashMap<>();
    private Semaphore writeCountMutex = new Semaphore(1);
    private volatile int writeResponseCount;

    public MutualExclusionClient(int clientId, int clientNum, int serverNum, int fileNum, int opCount) {
        super(Constant.BASE_CLIENT_PORT + clientId, Constant.CLIENT);
        this.clientId = clientId;
        this.clientNum = clientNum;
        this.serverNum = serverNum;
        this.fileNum = fileNum;
        this.opCount = opCount;

        logger = LogManager.getLogger("client" + clientId + "_logger");
    }

    @Override
    protected void startHandler(Connection connection, String clientType, int otherClientId) {
        switch (clientType) {
            case Constant.SERVER: {
                logger.trace("record connection from server" + otherClientId);
                if (connection == null) {
                    System.out.println("connection is null");
                }
                serverConnMap.put(otherClientId, connection);
                break;
            }
            case Constant.CLIENT: {
                logger.trace("record connection from client" + otherClientId);
                if (connection == null) {
                    System.out.println("connection is null");
                }
                clientConnMap.put(otherClientId, connection);
                break;
            }
        }
        ClientClientInboundMsgHandler inboundMsgHandler =
                new ClientClientInboundMsgHandler(connection, clientId, clientType + otherClientId + "--inbound", blockingQueueList);
        inboundMsgHandler.start();
        outboundBlockingQueueMap.put(otherClientId, new LinkedBlockingQueue<>());
        ClientClientOutboundMsgHandler outboundMsgHandler =
                new ClientClientOutboundMsgHandler(connection, clientId, clientType + otherClientId + "--outbound",
                        outboundBlockingQueueMap.get(otherClientId));
        outboundMsgHandler.start();
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

                ClientServerInboundMsgHandler handler =
                        new ClientServerInboundMsgHandler(client.getConnection(), clientId, threadName, this);
                handler.start();

                serverConnMap.put(port - Constant.BASE_SERVER_PORT, client.getConnection());
            } else if (split1[0].startsWith(Constant.CLIENT) && ((port - Constant.BASE_CLIENT_PORT) > clientId)) {
                // only connect to bigger "port" number, avoid duplicate connection
                BaseClient client = new BaseClient(split2[0], port, clientId, Constant.CLIENT);
                String threadName = Constant.CLIENT + (port - Constant.BASE_CLIENT_PORT);

                ClientClientInboundMsgHandler inboundMsgHandler =
                        new ClientClientInboundMsgHandler(client.getConnection(), clientId, threadName + "--inbound", blockingQueueList);
                inboundMsgHandler.start();

                outboundBlockingQueueMap.put(port - Constant.BASE_CLIENT_PORT, new LinkedBlockingQueue<>());
                ClientClientOutboundMsgHandler outboundMsgHandler =
                        new ClientClientOutboundMsgHandler(client.getConnection(), clientId, threadName + "--outbound",
                                outboundBlockingQueueMap.get(port - Constant.BASE_CLIENT_PORT));
                outboundMsgHandler.start();

                clientConnMap.put(port - Constant.BASE_CLIENT_PORT, client.getConnection());
            }
        }
    }

    /**
     * close all connections with server-side servers and client-side servers
     */
    public void closeConnection() {
        logger.trace("close all connections");
        for (RaAlgoWithCrOptimization raAlgoWithCrOptimization: meAlgoList) {
            raAlgoWithCrOptimization.tearDown();
        }
        try {
            for (Connection conn : serverConnMap.values()) {
                conn.writeUTF(Constant.EXIT);
                conn.closeConnection();
            }
            for (int target : clientConnMap.keySet()) {
                putIntoOutboundBlockingQueue(Constant.EXIT, target);
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
            LinkedBlockingQueue<String> inboundMsgBlockingQueue = new LinkedBlockingQueue<>();
            blockingQueueList.add(inboundMsgBlockingQueue);
            RaAlgoWithCrOptimization raAlgo =
                    new RaAlgoWithCrOptimization(clientId, clientNum, i, clientConnMap, this, inboundMsgBlockingQueue, outboundBlockingQueueMap);
            meAlgoList.add(raAlgo);
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
            System.out.println("finish all " + opCount + " operations");
            return;
        }
        int opId = Util.genRandom(3);
        opType = operations[opId];
        fileId = Util.genRandom(fileNum);
        curOpCount++;
        logger.trace("Request " + curOpCount + ": " + opType + " file" + fileId);

        // for ENQUIRY operation, no need to request resource
        if (opType.equals(Constant.REQ_SERVER_ENQUIRY)) {
            sendReq(Constant.REQ_SERVER_ENQUIRY);
            return;
        }

        curInboundMsgBlockingQueue = blockingQueueList.get(fileId);
        String reqMsg = Constant.INIT_REQUEST + " " + curOpCount;
        putIntoInboundBlockingQueue(reqMsg);
    }

    /**
     * enter critical section
     */
    public void enterCS() {
        sendReq(opType);
    }

    /**
     * finish critical section, execute next operation
     */
    public void finishCS() {
        executeOperation();
    }

    /**
     * finish read operation, prepare to release resources
     */
    public void finishReadOperation() {
        putIntoInboundBlockingQueue(Constant.FINISH_READ);
    }

    /**
     * check whether all write operations finish,
     * if not, wait for next response,
     * if yes, prepare to release resources
     */
    public void checkWriteFinish() {
        SemaUtil.wait(writeCountMutex);
        writeResponseCount++;
        if (writeResponseCount == serverNum) {
            // reset count
            writeResponseCount = 0;
            SemaUtil.signal(writeCountMutex);
            // write operation to all replicas succeed, release resource
            putIntoInboundBlockingQueue(Constant.FINISH_WRITE);
        } else {
            SemaUtil.signal(writeCountMutex);
        }
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
                break;
            }
            case Constant.REQ_SERVER_READ: {
                int id = Util.genRandom(serverNum);
                Connection connection = serverConnMap.get(id);
                String request = Constant.REQ_SERVER_READ + " " + fileId + " fromClient" + clientId + " requestNum" + curOpCount;
                connection.writeUTF(request);
                break;
            }
            case Constant.REQ_SERVER_WRITE: {
                long timestamp = System.currentTimeMillis();
                for (int i = 0; i < serverNum; i++) {
                    String content = "<" + clientId + "," + timestamp + ">";
                    String request = Constant.REQ_SERVER_WRITE + " " + fileId + " " + content + " fromClient" + clientId + " requestNum" + curOpCount;
                    serverConnMap.get(i).writeUTF(request);
                }
                break;
            }
            default:
                break;

        }
    }

    /**
     * put message into blocking queue, wait for mutual exclusion
     * algorithm's blocking queue to handle the message
     * @param msg inbound message
     */
    private void putIntoInboundBlockingQueue(String msg) {
        try {
            curInboundMsgBlockingQueue.put(msg);
            logger.trace("inbound msg: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting inbound msg: " + ex.toString());
        }
    }

    /**
     * put message into blocking queue, wait for client-client outbound handler's
     * blocking queue to handle the message
     * @param msg outbound message
     * @param target target client id
     */
    private void putIntoOutboundBlockingQueue(String msg, int target) {
        try {
            outboundBlockingQueueMap.get(target).put(msg);
            logger.trace("outbound msg: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting outbound msg: " + ex.toString());
        }
    }
}
