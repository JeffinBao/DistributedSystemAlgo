package network.server;

import algorithm.LamportMutualExclusion;
import constant.Constant;
import network.Connection;
import network.OutboundMessage;
import network.client.BaseClient;
import network.handler.LamportMEHandler;
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
    // we need to have the mutual exclusion algorithm object for each file
    private List<LamportMutualExclusion> meAlgoList = new ArrayList<>();
    private List<LinkedBlockingQueue<String>> blockingQueueList = new ArrayList<>();
    private Map<Integer, Connection> clientConnMap = new ConcurrentHashMap<>();
    private Map<Integer, Connection> serverConnMap = new ConcurrentHashMap<>();
    private String[] operations = {Constant.REQ_SERVER_ENQUIRY, Constant.REQ_SERVER_READ, Constant.REQ_SERVER_WRITE};
    private int curOpCount = 0;
    private String opType;
    private int fileId;
    private LamportMutualExclusion curMEAlgo;
    private LinkedBlockingQueue<String> curInboundMsgBlockingQueue;
    private Semaphore writeCountMutex = new Semaphore(1);
    private volatile int writeResponseCount;
    private boolean closeHandler;
    private LinkedBlockingQueue<OutboundMessage> outboundMessageBlockingQueue = new LinkedBlockingQueue<>();

    public MutualExclusionClient(int clientId, int clientNum, int serverNum, int fileNum, int opCount) {
        super(Constant.BASE_CLIENT_PORT + clientId, Constant.CLIENT);
        this.clientId = clientId;
        this.clientNum = clientNum;
        this.serverNum = serverNum;
        this.fileNum = fileNum;
        this.opCount = opCount;

        logger = LogManager.getLogger("client" + clientId + "_logger");
        new MessageSendHandler().start();
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
        LamportMEHandler handler = new LamportMEHandler(connection, clientId, clientType + otherClientId, blockingQueueList, this);
        handler.start();
    }

    public void enterCS() {
        sendReq(opType);
    }

    public void finishCS() {
        executeOperation();
    }

    public void checkWriteComplete() {
        SemaUtil.wait(writeCountMutex);
        writeResponseCount++;
        if (writeResponseCount == serverNum) {
            // reset count
            writeResponseCount = 0;
            SemaUtil.signal(writeCountMutex);
            // write operation to all replicas succeed, release resource
            putIntoBlockingQueue(Constant.INIT_RELEASE);
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
                LamportMEHandler handler = new LamportMEHandler(client.getConnection(), clientId, threadName, blockingQueueList, this);
                handler.start();
                serverConnMap.put(port - Constant.BASE_SERVER_PORT, client.getConnection());
            } else if (split1[0].startsWith(Constant.CLIENT) && ((port - Constant.BASE_CLIENT_PORT) > clientId)) {
                // TODO only connect to bigger "port" number, avoid duplicate connection
                // TODO current implementation is not a good way.
                BaseClient client = new BaseClient(split2[0], port, clientId, Constant.CLIENT);
                String threadName = Constant.CLIENT + (port - Constant.BASE_CLIENT_PORT);
                LamportMEHandler handler = new LamportMEHandler(client.getConnection(), clientId, threadName, blockingQueueList, this);
                handler.start();
                clientConnMap.put(port - Constant.BASE_CLIENT_PORT, client.getConnection());
            }
        }
    }

    /**
     * close all connections with server-side servers and client-side servers
     */
    public void closeConnection() {
        logger.trace("close all connections");
        for (LamportMutualExclusion lamportME: meAlgoList) {
            lamportME.tearDown();
        }
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
        // close MessageSendHandler
        closeHandler = true;
    }

    /**
     * init mutual exclusion algorithm list
     * each file has its own mutual exclusion algorithm object
     */
    public void initMEList() {
        for (int i = 0; i < fileNum; i++) {
            LinkedBlockingQueue<String> inboundMsgBlockingQueue = new LinkedBlockingQueue<>();
            blockingQueueList.add(inboundMsgBlockingQueue);
            meAlgoList.add(new LamportMutualExclusion(clientId, clientNum, i, clientConnMap, this, inboundMsgBlockingQueue, outboundMessageBlockingQueue));
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
        logger.trace("Request " + curOpCount + ": " + opType + " file" + fileId);

        // for ENQUIRY operation, no need to request resource
        if (opType.equals(Constant.REQ_SERVER_ENQUIRY)) {
            sendReq(Constant.REQ_SERVER_ENQUIRY);
            return;
        }

        curMEAlgo = meAlgoList.get(fileId);
        curInboundMsgBlockingQueue = blockingQueueList.get(fileId);
        String reqMsg = Constant.INIT_REQUEST + " " + opType + " " + curOpCount;
        putIntoBlockingQueue(reqMsg);
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

    private void putIntoBlockingQueue(String msg) {
        try {
            curInboundMsgBlockingQueue.put(msg);
            logger.trace("inbound msg: " + msg);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            logger.trace("failed inserting inbound msg: " + ex.toString());
        }
    }

    private class MessageSendHandler extends Thread {
        @Override
        public void run() {
            while (!closeHandler) {
                try {
                    OutboundMessage outboundMessage = outboundMessageBlockingQueue.take();
                    clientConnMap.get(outboundMessage.getTarget()).writeUTF(outboundMessage.getMessage());
                    logger.trace("sent outboundMsg: " + outboundMessage.getMessage() + " to client " + outboundMessage.getTarget());
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                    logger.trace("failed sending outboundMsg: " + ex.toString());
                    break;
                }
            }
        }
    }

}
