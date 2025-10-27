package com.smartfarm.smartfarm_server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.smartfarm_server.model.SensorData;
import com.smartfarm.smartfarm_server.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

@Configuration
@RequiredArgsConstructor
@Slf4j

public class MqttSubscriberConfig {

    private final SensorDataRepository sensorDataRepository;
    private final ObjectMapper objectMapper;

    // TODO: Mosquitto 브로커 VM의 외부 IP 주소로 변경하세요.
    private static final String MQTT_BROKER_URL = "";
    private static final String MQTT_CLIENT_ID = "datafarm-server";
    private static final String MQTT_TOPIC = "datafarm/sensor_data"; // 이미지의 토픽으로 수정

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(MQTT_BROKER_URL, MQTT_CLIENT_ID, MQTT_TOPIC);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());

        return adapter;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMqttMessage(String payload) {
        log.info("Received MQTT message: " + payload);
        try {
            // ObjectMapper가 SensorData 클래스의 @JsonFormat 설정을 자동으로 처리합니다.
            SensorData sensorData = objectMapper.readValue(payload, SensorData.class);

            // 데이터베이스 저장 전, 파싱된 객체의 값들을 로그로 출력합니다.
            log.info("======================================================");
            log.info("Payload successfully parsed. Check the values below:");
            log.info("SensorData Object: {}", sensorData.toString());
            log.info("Timestamp value: {}", sensorData.getTimestamp());
            log.info("------------------------------------------------------");

            sensorDataRepository.save(sensorData);
            log.info("Successfully saved sensor data to database: {}", sensorData.toString());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON payload", e);
        }
    }
}
