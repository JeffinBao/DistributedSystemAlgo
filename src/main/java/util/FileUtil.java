package util;

import constant.Constant;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Author: JeffinBao
 * Date: 2019-09-08
 * Usage: File util for file related operations
 */
public class FileUtil {

    /**
     * return a string consisting of all file names
     * in a specific directory
     * @param directory directory path
     * @return string without front and tail whitespace
     */
    public static String enquire(String directory) {
        StringBuilder sb = new StringBuilder();
        File[] files = new File(directory).listFiles();
        if (files == null)
            return sb.toString();

        for (File file : files) {
            if (file.isFile()) {
                sb.append(file.getName());
                sb.append(" ");
            }
        }

        // remove the last white space
        return sb.toString().trim();
    }

    /**
     * read last line of a file
     * @param filePath file path
     * @return last line
     */
    public static String readLastLine(String filePath) {
        File file = new File(filePath);
        String result = "";
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            // TODO maybe we can start from raf.length() - 2
            // TODO skip the last '\n'
            // initial last pointer
            long filePointer = raf.length() - 1;
            StringBuilder sb = new StringBuilder();
            for (; filePointer >= 0;filePointer--) {
                raf.seek(filePointer);
                int curByte = raf.readByte();

                if ((char) curByte == '\n') {
                    // if it is the last '\n', continue the loop
                    // otherwise, break, means we found the last line
                    if (filePointer == raf.length() - 1)
                        continue;
                    break;
                }
                sb.append((char) curByte);
            }

            // reverse the string
            result = sb.reverse().toString();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            // close resources if necessary
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        return result;
    }

    /**
     * read a file line by line
     * @param filePath file path
     * @return a set of containing all line in the file
     */
    public static Set<String> readAllLines(String filePath) {
        Set<String> set = new HashSet<>();
        try {
            FileInputStream fis = new FileInputStream(filePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            String line;
            while ((line = br.readLine()) != null) {
                set.add(line);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return set;
    }

    /**
     * write to the end of a file
     * @param filePath file path
     * @param content content to be written
     * @return "success" if write is successful, otherwise return "fail"
     */
    public static String write(String filePath, String content) {
        FileWriter fw = null;
        try {
            // append mode
            fw = new FileWriter(filePath, true);
            fw.write(content + System.lineSeparator());
        } catch (IOException ex) {
            ex.printStackTrace();
            return Constant.FAIL;
        } finally {
            // close resources if necessary
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        return Constant.SUCCESS;
    }
}
