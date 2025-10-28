package com.smartfarm.smartfarm_server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String redirectToDashboard() {
        return "redirect:/dashboard"; // ✅ / 로 들어와도 /dashboard 로 이동
    }

    @GetMapping("/camera")
    public String cameraPage() {
        return "camera";
    }

    @GetMapping("/chatbot")
    public String chatbotPage() {
        return "chatbot";
    }

    @GetMapping("/harvest")
    public String harvestPage() {
        return "harvest";
    }
}
