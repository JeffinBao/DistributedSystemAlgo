package util;

import java.util.concurrent.Semaphore;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: Semaphore util to control a semaphore
 */
public class SemaUtil {

    /**
     * wait on a semaphore
     * @param sema input semaphore
     */
    public static void wait(Semaphore sema) {
        try {
            sema.acquire();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * signal a semaphore
     * @param sema input semaphore
     */
    public static void signal(Semaphore sema) {
        sema.release();
    }
}
