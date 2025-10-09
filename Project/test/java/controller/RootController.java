package com.smartfarm.smartfarm_server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String homePage() {
        return "index"; // src/main/resources/templates/index.html을 반환
    }

    @GetMapping("/camera")
    public String cameraPage() {
        return "camera"; // src/main/resources/templates/camera.html을 반환
    }
}