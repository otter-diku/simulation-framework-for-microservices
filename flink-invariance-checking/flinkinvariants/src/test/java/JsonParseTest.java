

import static org.junit.jupiter.api.Assertions.assertEquals;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;


public class JsonParseTest {
    @Test
    void GetBasketFromCheckout() {
        String jsonFile = "CheckoutEvent.json";

        String content = GetFileContentAsString(jsonFile);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode items = jsonNode.get("Basket").get("Items");

            Set<Integer> productIds = Set.of(1,2,6);
            items.forEach(item ->
                    Assertions.assertTrue(productIds.contains(item.get("ProductId").asInt()))
            );
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
