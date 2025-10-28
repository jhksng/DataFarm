package com.smartfarm.smartfarm_server.service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MqttPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(MqttPublisherService.class);

    @Autowired
    private MqttClient mqttClient;

    public void publish(String topic, String payload) {
        try {
            // ✨ 추가된 로직: 발행 전 연결 상태 확인 및 재연결
            if (!mqttClient.isConnected()) {
                logger.warn("⚠️ MQTT 클라이언트가 연결되지 않았습니다. 재연결을 시도합니다.");
                mqttClient.reconnect();
            }

            MqttMessage message = new MqttMessage(payload.getBytes());
            // QoS 설정은 필요에 따라 추가
            // message.setQos(1);
            mqttClient.publish(topic, message);
            logger.info("✨ MQTT 발행: 토픽 '{}', 메시지 '{}'", topic, payload);
        } catch (MqttException e) {
            logger.error("❌ MQTT 메시지 발행 실패: {}", e.getMessage());
        }
    }
}