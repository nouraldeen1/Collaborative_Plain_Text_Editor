package util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import model.Operation;
import java.util.List;

public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

    // Serialize an object to JSON string
    public static String toJson(Object obj) throws Exception {
        return writer.writeValueAsString(obj);
    }

    // Deserialize a JSON string to an object of the specified class
    public static <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return mapper.readValue(json, clazz);
    }

    // Deserialize a JSON string to a parameterized type (like List<T>)
    public static <T> T fromJson(String json, TypeReference<T> typeReference) throws Exception {
        return mapper.readValue(json, typeReference);
    }
}

    // Helper