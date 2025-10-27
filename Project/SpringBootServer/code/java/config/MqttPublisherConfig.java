package com.smartfarm.smartfarm_server.config;

import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttPublisherConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    // 고정된 클라이언트 ID를 사용하도록 수정
    private static final String CLIENT_ID = "smartfarm-server-publisher";

    @Bean
    public MqttClient mqttClient() throws MqttException {
        // 고정된 클라이언트 ID로 MqttClient 생성
        MqttClient mqttClient = new MqttClient(brokerUrl, CLIENT_ID);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        try {
            mqttClient.connect(options);
            System.out.println("✅ MQTT 브로커에 성공적으로 연결되었습니다.");
        } catch (MqttException e) {
            System.err.println("❌ MQTT 브로커 연결 실패: " + e.getMessage());
        }

        return mqttClient;
    }
}