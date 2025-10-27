package com.smartfarm.smartfarm_server.config;

import com.smartfarm.smartfarm_server.repository.CropInfoRepository;
import com.smartfarm.smartfarm_server.service.PhotoService;
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
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MqttPhotoSubscriberConfig {

    private final PhotoService photoService;
    private final CropInfoRepository cropInfoRepository;

    private static final String MQTT_BROKER_URL = "";
    private static final String MQTT_CLIENT_ID = "datafarm-photo-subscriber";
    private static final String MQTT_TOPIC_PHOTO = "photo/uploaded";

    @Bean
    public MessageChannel mqttPhotoInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttPhotoInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(MQTT_BROKER_URL, MQTT_CLIENT_ID, MQTT_TOPIC_PHOTO);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttPhotoInputChannel());
        return adapter;
    }

    @ServiceActivator(inputChannel = "mqttPhotoInputChannel")
    public void handlePhotoMqttMessage(Message<?> message) {
        String payload = (String) message.getPayload();
        log.info("Received photo URL from topic: {}, payload: {}", message.getHeaders().get("mqtt_receivedTopic"), payload);

        String photoUrl = payload;
        String cropName = cropInfoRepository.findByIsActiveTrue()
                .map(crop -> crop.getCrop())
                .orElse("No Active Crop");

        photoService.savePhoto(photoUrl, cropName);

        log.info("Successfully saved photo information to database.");
    }
}