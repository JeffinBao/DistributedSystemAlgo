package network;

/**
 * Author: JeffinBao
 * Date: 2019-10-23
 * Usage: Store outbound message info among client side servers
 */
public class OutboundMessage {
    private int target;
    private String message;

    public OutboundMessage(int target, String message) {
        this.target = target;
        this.message = message;
    }

    /**
     * get target client id
     * @return id
     */
    public int getTarget() {
        return target;
    }

    /**
     * get outbound message string
     * @return message
     */
    public String getMessage() {
        return message;
    }
}
