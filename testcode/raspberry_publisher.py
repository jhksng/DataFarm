# 이 스크립트는 라즈베리파이에서 실행됩니다.
import paho.mqtt.client as mqtt
import json
import time
import random

# TODO: GCP Mosquitto 브로커 VM의 실제 외부 IP 주소로 변경하세요.
MQTT_BROKER_IP = "p"
MQTT_BROKER_PORT = 1883
# TODO: 센서 데이터를 보낼 토픽 이름으로 변경하세요.
MQTT_TOPIC = ""

def on_connect(client, userdata, flags, rc):
    """MQTT 브로커에 연결되었을 때 호출되는 콜백 함수"""
    print(f"Connected with result code {rc}")

def publish_sensor_data():
    """
    임의의 센서 데이터를 생성하여 MQTT 브로커로 발행(Publish)합니다.
    """
    try:
        data = {
            "soilMoisture": random.uniform(0.0, 100.0),
            "waterLevel": random.randint(0, 100),
            "temperature": random.uniform(15.0, 35.0),
            "humidity": random.uniform(30.0, 80.0),
            "raspberryId": "raspi-01",
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
        }
        json_data = json.dumps(data)
        client.publish(MQTT_TOPIC, json_data)
        print(f"데이터 발행: {json_data}")
    
    except Exception as e:
        print(f"데이터 전송 오류: {e}")

# MQTT 클라이언트 설정
client = mqtt.Client()
client.on_connect = on_connect

# 브로커에 연결
client.connect(MQTT_BROKER_IP, MQTT_BROKER_PORT, 60)
client.loop_start()

if __name__ == "__main__":
    try:
        while True:
            publish_sensor_data()
            time.sleep(10) # 10초마다 데이터 전송
    except KeyboardInterrupt:
        print("스크립트 종료")
        client.loop_stop()
        client.disconnect()
