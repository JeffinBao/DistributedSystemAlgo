package model;

/**
 * Author: JeffinBao
 * Date: 2019-10-12
 * Usage: request, comparable based on sequence number and request origin id
 */
public class Request implements Comparable<Request> {
    private int requestNum;
    private int sequenceNum;
    private int requestOriginId;

    public Request(int requestNum, int sequenceNum, int requestOriginId) {
        this.requestNum = requestNum;
        this.sequenceNum = sequenceNum;
        this.requestOriginId = requestOriginId;
    }

    public int getRequestNum() {
        return requestNum;
    }

    public int getSequenceNum() {
        return sequenceNum;
    }

    public int getRequestOriginId() {
        return requestOriginId;
    }

    public int compareTo(Request o) {
        int otherSeqNum = o.getSequenceNum();
        int otherReqOriId = o.getRequestOriginId();
        if (sequenceNum != otherSeqNum) {
            return sequenceNum - otherSeqNum;
        } else {
            return requestOriginId - otherReqOriId;
        }
    }
}
