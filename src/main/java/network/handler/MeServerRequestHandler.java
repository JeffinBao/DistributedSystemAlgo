package network.handler;

import constant.Constant;
import network.Connection;
import util.FileUtil;

/**
 * Author: JeffinBao
 * Date: 2019-09-17
 * Usage: server-side server request handler
 */
public class MeServerRequestHandler extends RequestHandler {
    private String workingDir;

    public MeServerRequestHandler(Connection connection, int id, String name) {
        super(connection, id, name);
        workingDir = System.getProperty("user.dir") + "/" + Constant.BASE_DIRECTORY_PATH + id;
    }

    @Override
    protected void handleMsg(String msg) {
        String response = "";
        String[] split = msg.split(" ", 2);
        System.out.println("receive operation: " + msg + " from Thread " + Thread.currentThread().getName());
        switch (split[0]) {
            case Constant.REQ_SERVER_ENQUIRY: {
                // enquiry request format: "req_server_enq fromClientX requestNumX"
                response = Constant.REPLY_SERVER_ENQUIRY + " " + split[1] + FileUtil.enquire(workingDir);
                break;
            }
            case Constant.REQ_SERVER_READ: {
                // read request format: "req_server_read fileId fromClientX requestNumX"
                String[] split1 = split[1].split(" ");
                String fileName = "file" + split1[0] + ".txt";
                response = Constant.REPLY_SERVER_READ + " " + split1[0] + " " + FileUtil.readLastLine(workingDir + "/" + fileName) + split[1];
                break;
            }
            case Constant.REQ_SERVER_WRITE: {
                // write request format: "req_server_write fileId content fromClientX requestNumX"
                String[] split1 = split[1].split(" ");
                String fileName = "file" + split1[0] + ".txt";
                response = Constant.REPLY_SERVER_WRITE + " " + FileUtil.write(workingDir + "/" + fileName, split1[1]) + split[1];
                break;
            }
            default:
                response = Constant.COMMAND_INVALID;
        }

        connection.writeUTF(response);
        System.out.println("response to Thread " + Thread.currentThread().getName() + ": " + response);
    }
}
