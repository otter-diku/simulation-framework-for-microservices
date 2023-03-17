

import static org.junit.jupiter.api.Assertions.assertEquals;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class JsonParseTest {
    @Test
    void GetBasketFromCheckout() {
        String jsonFile = "CheckoutEvent.json";

        String content = GetFileContentAsString(jsonFile);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode basket = jsonNode.get("Basket");
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String GetFileContentAsString(String file) {
        String fileName = System.getProperty("user.dir") + "/src/test/java/" + file;
        try {
            String content = Files.readString(Paths.get(fileName));
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
