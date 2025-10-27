package com.smartfarm.smartfarm_server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
public class GeminiSimpleController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    @PostMapping("/gemini/simple")
    public String callGemini(@RequestBody Map<String, String> body) {
        String input = body.get("input");
        String context = body.getOrDefault("context", "");

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text",
                                """
                                당신은 30년 경력의 베테랑 농부입니다.
                                친근하고 따뜻한 말투로, "~에요", "~한 거예요" 식으로 말합니다.
                                너무 길게 말하지 말고, 핵심 위주로 3~5문장 이내로 대답합니다.
                                필요하면 "원하시면 더 자세히 알려드릴게요."로 마무리합니다.
                                
                                ⚠️ 절대로 거짓된 정보를 말하지 않습니다.
                                사실에 근거하여 대답하고, 모르는 내용은 "그건 잘 모르겠어요."라고 정직하게 말합니다.
        
                                지금까지의 대화 맥락:
                                """ + context + "\n\n사용자: " + input
                        ))
                ))
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode node = objectMapper.readTree(response.getBody());
            return node.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "⚠️ 오류: Gemini API 호출 중 문제가 발생했어요.";
        }
    }
}
