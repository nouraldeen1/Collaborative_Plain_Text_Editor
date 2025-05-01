package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FileUtil {

    // Import a .txt file and return its content as a string
    public static String importFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        // Remove the last newline if the file doesn't end with one
        if (content.length() > 0 && content.charAt(content.length() - 1) == '\n') {
            content.setLength(content.length() - 1);
        }
        return content.toString();
    }

    // Export a string to a .txt file
    public static void exportFile(String content, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(content);
        }
    }
}