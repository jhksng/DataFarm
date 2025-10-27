package com.smartfarm.smartfarm_server.config;

import com.smartfarm.smartfarm_server.service.PhotoService;
import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.dto.PhotoAnalysisDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MqttAnalysisSubscriberConfig {

    private final PhotoService photoService;
    private final CropInfoRepository cropInfoRepository;
    private final ObjectMapper objectMapper; // ObjectMapper 주입

    private static final String MQTT_BROKER_URL = ""; // 기존 값 사용
    private static final String ANALYSIS_CLIENT_ID = "datafarm-analysis-subscriber"; // 새 클라이언트 ID
    private static final String ANALYSIS_TOPIC = "analysis/result";
    private static final String PHOTO_URL_PREFIX = "datafarm-picture/"; // PhotoMqttMessageHandler와 동일

    @Bean
    public MessageChannel mqttAnalysisInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttAnalysisInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(MQTT_BROKER_URL, ANALYSIS_CLIENT_ID, ANALYSIS_TOPIC);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttAnalysisInputChannel());
        return adapter;
    }

    @ServiceActivator(inputChannel = "mqttAnalysisInputChannel")
    public void handleAnalysisMqttMessage(Message<?> message) {
        String payload = (String) message.getPayload();
        log.info("Analysis message received: Topic={}, Payload={}", message.getHeaders().get("mqtt_receivedTopic"), payload);

        try {
            PhotoAnalysisDTO analysisDTO = objectMapper.readValue(payload, PhotoAnalysisDTO.class);
            String rawFileName = analysisDTO.getFileName();
            Float brightnessRatio = analysisDTO.getBrightnessRatio();
            String dbPhotoUrl = PHOTO_URL_PREFIX + rawFileName;

            // 밝기 비율이 존재할 때만 업데이트 시도 (PhotoMqttMessageHandler의 최종 로직)
            if (brightnessRatio != null) {
                photoService.updatePhotoRatioByPhotoUrl(dbPhotoUrl, brightnessRatio);
                log.info("✅ 사진 분석 값 업데이트 완료 요청: DB URL={}, 밝기 비율={}", dbPhotoUrl, brightnessRatio);
            }

        } catch (Exception e) {
            log.error("❌ MQTT 분석 메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}