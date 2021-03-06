package constant;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: Store constant values
 */
public class Constant {
    public static final int BASE_SERVER_PORT = 6000;
    public static final int BASE_CLIENT_PORT = 7000;
    public static final String BASE_DIRECTORY_PATH = "backend_server";
    public static final String MACHINE_INFO_FILE_PATH = "machine_info.txt";
    public static final String BASE_LOG_PATH = "log";

    public static final String SUCCESS = "success";
    public static final String FAIL = "fail";

    public static final String EXIT = "exit";
    public static final String EMPTY_STRING = "";

    public static final String REQ_ME = "req_me";
    public static final String REQ_SERVER_ENQUIRY = "req_server_enq";
    public static final String REQ_SERVER_READ = "req_server_read";
    public static final String REQ_SERVER_WRITE = "req_server_write";
    public static final String REPLY_ME = "reply_me";
    public static final String REPLY_SERVER_ENQUIRY = "reply_server_enq";
    public static final String REPLY_SERVER_READ = "reply_server_read";
    public static final String REPLY_SERVER_WRITE = "reply_server_write";
    public static final String RELEASE_ME = "release_me";
    public static final String FINISH_READ = "finish_read";
    public static final String FINISH_WRITE = "finish_write";
    public static final String INIT_REQUEST = "init_req";

    public static final String SERVER = "server";
    public static final String CLIENT = "client";

    public static final String COMMAND_CONNECT = "connect";
    public static final String COMMAND_RECONNECT = "reconnect";
    public static final String COMMAND_INVALID = "invalid command";
    public static final String COMMAND_CLOSE = "close";

    public static final String ALGO_RA_WITH_OPTIMIZATION = "ra_with_optimization";
    public static final String ALGO_LAMPORT = "lamport";

    public static final String COUNT_INBOUND_MSG = "count_inbound_msg";
    public static final String COUNT_OUTBOUND_MSG = "count_outbound_msg";
}
