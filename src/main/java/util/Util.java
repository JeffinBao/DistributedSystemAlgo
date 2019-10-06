package util;

import java.util.Random;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: General util
 */
public class Util {

    /**
     * generate random integer according to the seed value
     * @param seed seed
     * @return a random integer
     */
    public static int genRandom(int seed) {
        Random random = new Random();
        return random.nextInt(seed);
    }
}
