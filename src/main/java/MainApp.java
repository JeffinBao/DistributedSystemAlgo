import constant.Constant;
import network.server.MutualExclusionClient;
import network.server.MutualExclusionServer;
import util.FileUtil;

import java.io.File;
import java.util.Scanner;

/**
 * Author: JeffinBao
 * Date: 2019-09-17
 * Usage: Main entrance to run different distributed algorithms
 */
public class MainApp {
    public static void main(String[] args) {
        String serverType = args[0];
        switch (serverType) {
            case Constant.SERVER: {
                int serverId = Integer.parseInt(args[1]);
                int fileNum = 3;
                if (args.length == 3) {
                    fileNum = Integer.parseInt(args[2]);
                }

                String workingDir = System.getProperty("user.dir") + "/" + Constant.BASE_DIRECTORY_PATH + serverId;
                File directory = new File(workingDir);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                // every time before server starts, first clean all files
                for (int i = 0; i < fileNum; i++) {
                    File file = new File(workingDir + "/file" + i + ".txt");
                    if (file.exists()) {
                        file.delete();
                    }
                    FileUtil.write(workingDir + "/file" + i + ".txt", "This is the beginning of file" + i);
                }

                MutualExclusionServer serverSideServer = new MutualExclusionServer(serverId);
                // wait command to initialize the connection
                Scanner scanner = new Scanner(System.in);
                String command = scanner.nextLine();
                if (command.equals(Constant.COMMAND_CLOSE)) {
                    System.exit(1);
                }
                break;
            }
            case Constant.CLIENT: {
                int clientId = Integer.parseInt(args[1]);
                int clientNum = 5;
                int serverNum = 3;
                int fileNum = 2;
                int opCount = 10000;
                if (args.length == 6) {
                    clientNum = Integer.parseInt(args[2]);
                    serverNum = Integer.parseInt(args[3]);
                    fileNum = Integer.parseInt(args[4]);
                    opCount = Integer.parseInt(args[5]);
                }
                MutualExclusionClient clientSideServer = new MutualExclusionClient(clientId, clientNum, serverNum, fileNum, opCount);
                // wait command to initialize the connection
                Scanner scanner = new Scanner(System.in);
                String command = scanner.nextLine();
                if (!command.equals(Constant.COMMAND_CONNECT)) {
                    System.out.println(Constant.COMMAND_INVALID);
                    System.exit(1);
                }

                clientSideServer.initConnection();
                clientSideServer.initMEList();

                while (true) {
                    command = scanner.nextLine();
                    if (command.equals(Constant.COMMAND_RECONNECT)) {
                        clientSideServer.closeConnection();
                        clientSideServer.clearMEList();

                        clientSideServer.initConnection();
                        clientSideServer.initMEList();
                    } else if (command.equals(Constant.COMMAND_CLOSE)) {
                        clientSideServer.closeConnection();
                        break;
                    } else {
                        clientSideServer.executeOperation();
                    }
                }
                // delete machine_info.txt file if it exists
                File file = new File(System.getProperty("user.dir") + "/" + Constant.MACHINE_INFO_FILE_PATH);
                if (file.exists()) {
                    file.delete();
                }
                System.exit(1);
                break;
            }
        }
    }
}
