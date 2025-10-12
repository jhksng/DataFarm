package com.smartfarm.smartfarm_server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString; // toString() 메서드 자동 생성
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JSON 필드명과 일치시키기 위해 @JsonProperty 사용
    @JsonProperty("raspberryId")
    private String raspberryId;

    @JsonProperty("soilMoisture")
    private double soilMoisture;

    @JsonProperty("waterLevel")
    private int waterLevel;

    @JsonProperty("temperature")
    private double temperature;

    @JsonProperty("humidity")
    private double humidity;

   @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime timestamp;


}