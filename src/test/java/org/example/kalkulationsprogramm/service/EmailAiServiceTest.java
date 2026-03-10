//package org.example.kalkulationsprogramm.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//public class EmailAiServiceTest {
//
//    private String callExtract(EmailAiService svc, String json) throws Exception {
//        Method m = EmailAiService.class.getDeclaredMethod("extractContent", com.fasterxml.jackson.databind.JsonNode.class);
//        m.setAccessible(true);
//        return (String) m.invoke(svc, new ObjectMapper().readTree(json));
//    }
//
//    @Test
//    void extractsFromOllamaChat() throws Exception {
//        EmailAiService svc = new EmailAiService(new ObjectMapper());
//        String json = "{\n" +
//                "  \"message\": { \"role\": \"assistant\", \"content\": \"Hallo Welt\" },\n" +
//                "  \"done\": true\n" +
//                "}";
//        String content = callExtract(svc, json);
//        assertEquals("Hallo Welt", content);
//    }
//
//    @Test
//    void extractsFromOllamaGenerate() throws Exception {
//        EmailAiService svc = new EmailAiService(new ObjectMapper());
//        String json = "{\n" +
//                "  \"response\": \"Hallo aus Generate\"\n" +
//                "}";
//        String content = callExtract(svc, json);
//        assertEquals("Hallo aus Generate", content);
//    }
//
//    @Test
//    void extractsFromOpenAIChoices() throws Exception {
//        EmailAiService svc = new EmailAiService(new ObjectMapper());
//        String json = "{\n" +
//                "  \"choices\": [ { \"message\": { \"content\": \"Hallo OpenAI\" } } ]\n" +
//                "}";
//        String content = callExtract(svc, json);
//        assertEquals("Hallo OpenAI", content);
//    @Test
//    void testProcessBeautify_truncatesLongText() throws Exception {
//        // 1. Setup
//        EmailAiService service = new EmailAiService(new ObjectMapper());
//
//        // Use reflection to set the 'enabled' field to false to prevent real API calls.
//        java.lang.reflect.Field enabledField = EmailAiService.class.getDeclaredField("enabled");
//        enabledField.setAccessible(true);
//        enabledField.set(service, false);
//
//        // Create a string longer than the limit (25,000 characters).
//        int limit = 25_000;
//        StringBuilder longTextBuilder = new StringBuilder();
//        for (int i = 0; i < limit + 100; i++) {
//            longTextBuilder.append("a");
//        }
//        String longText = longTextBuilder.toString();
//        assertTrue(longText.length() > limit);
//
//        // 2. Execution
//        // Use reflection to access the private 'processBeautify' method.
//        Method processBeautifyMethod = EmailAiService.class.getDeclaredMethod("processBeautify", String.class, String.class);
//        processBeautifyMethod.setAccessible(true);
//        String result = (String) processBeautifyMethod.invoke(service, longText, null);
//
//        // 3. Assertion
//        // With 'enabled=false', the method should return the truncated plain text.
//        // The truncation happens before the sanitizer, and since our input is plain text,
//        // the result should be the truncated string.
//        assertEquals(limit, result.length());
//        assertEquals(longText.substring(0, limit), result);
//    }
//}

