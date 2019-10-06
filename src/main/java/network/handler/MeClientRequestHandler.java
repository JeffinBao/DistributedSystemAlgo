package network.handler;

import algorithm.CriticalSectionCallback;
import algorithm.RaAlgoWithCrOptimization;
import constant.Constant;
import network.Connection;
import java.util.List;

/**
 * Author: JeffinBao
 * Date: 2019-09-18
 * Usage: Client-side server request handler
 */
public class MeClientRequestHandler extends RequestHandler {
    private List<RaAlgoWithCrOptimization> meAlgoList;
    private CriticalSectionCallback csCallback;

    public MeClientRequestHandler(Connection connection, int id, String name,
                                  List<RaAlgoWithCrOptimization> meAlgoList, CriticalSectionCallback csCallback) {
        super(connection, id, name);
        this.meAlgoList = meAlgoList;
        this.csCallback = csCallback;
    }

    @Override
    protected void handleMsg(String msg) {
        // TODO we need to add a queue to store the incoming msg,
        // it is possible that REQUEST and REPLY are coming at the same time.
        System.out.println("received msg: " + msg + " from Thread: " + Thread.currentThread().getName());
        String[] split = msg.split(" ", 2);
        switch (split[0]) {
            case Constant.REQ_ME: {
                // Format: Constant.REQ_ME + " " + ourSeqNum + " " + me + " " + fileId + " " + requestNum
                String[] split1 = split[1].split(" ");
                int theirSeqNum = Integer.parseInt(split1[0]);
                int otherClientId = Integer.parseInt(split1[1]);
                int fileId = Integer.parseInt(split1[2]);
                int otherRequestNum = Integer.parseInt(split1[3]);

                RaAlgoWithCrOptimization mutualExclusion = meAlgoList.get(fileId);
                mutualExclusion.treatRequestMsg(connection, theirSeqNum, otherClientId, otherRequestNum);
                System.out.println("otherRequestNum: " + otherRequestNum + " file " + fileId + " -- client request handler: finish treatRequestMsg");
                break;
            }
            case Constant.REPLY_ME: {
                // Format: Constant.REPLY_ME + " " + me + " " + fileId
                String[] split1 = split[1].split(" ");
                RaAlgoWithCrOptimization mutualExclusion = meAlgoList.get(Integer.parseInt(split1[1]));
                mutualExclusion.treatReplyMsg(Integer.parseInt(split1[0]));
                break;
            }
            case Constant.REPLY_SERVER_ENQUIRY: {
                // Format: Constant.REPLY_SERVER_ENQUIRY fromClientX requestNumX response
                csCallback.finishCS();
                break;
            }
            case Constant.REPLY_SERVER_READ: {
                // Format: Constant.REPLY_SERVER_READ + " " + fileId + " " + response
                String[] split1 = split[1].split(" ");
                int fileId = Integer.parseInt(split1[0]);
                RaAlgoWithCrOptimization mutualExclusion = meAlgoList.get(fileId);
                mutualExclusion.releaseResource();
                break;
            }
            case Constant.REPLY_SERVER_WRITE: {
                // Format: Constant.REPLY_SERVER_WRITE + " " + response
                csCallback.checkWriteComplete();
                break;
            }
        }
    }
}
