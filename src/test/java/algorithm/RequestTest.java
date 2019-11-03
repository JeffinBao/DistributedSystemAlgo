package algorithm;

import model.Request;
import org.junit.Assert;
import org.junit.Test;

import java.util.PriorityQueue;

/**
 * Author: JeffinBao
 * Date: 2019-10-12
 * Usage: Test whether comparable Request class will be sorted as expected
 */
public class RequestTest {

    @Test
    public void testRequestClass() {
        Request request1 = new Request(0, 1, 0);
        Request request2 = new Request(2, 1, 1);
        Request request3 = new Request(3, 3, 0);

        PriorityQueue<Request> pq = new PriorityQueue<Request>();
        pq.offer(request3);
        pq.offer(request1);
        pq.offer(request2);

        Request first = pq.poll();
        Request second = pq.poll();
        Request third = pq.poll();
        Assert.assertEquals(1, first.getSequenceNum());
        Assert.assertEquals(0, first.getRequestOriginId());
        Assert.assertEquals(1, second.getSequenceNum());
        Assert.assertEquals(1, second.getRequestOriginId());
        Assert.assertEquals(3, third.getSequenceNum());
        Assert.assertEquals(0, third.getRequestOriginId());
    }
}
