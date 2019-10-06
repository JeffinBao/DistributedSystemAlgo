package algorithm;

/**
 * Author: JeffinBao
 * Date: 2019-09-10
 * Usage: Critical section callback
 */
public interface CriticalSectionCallback {

    /**
     * enter into critical section
     */
    void enterCS();

    /**
     * critical section finishes
     */
    void finishCS();

    /**
     * check whether write operation to the backend server has completed
     */
    void checkWriteComplete();
}
