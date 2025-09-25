# -*- coding: utf-8 -*-

import paho.mqtt.client as mqtt
import json
import time
import sys
import serial
import board
import adafruit_sht31d
import RPi.GPIO as GPIO
import threading

# --- MQTT 설정 ---
# TODO: GCP Mosquitto 브로커 VM의 실제 외부 IP 주소로 변경하세요.
MQTT_BROKER_IP = "34.64.167.185"
MQTT_BROKER_PORT = 1883
# TODO: 라즈베리파이의 고유 ID로 변경하세요.
DEVICE_ID = "raspi-01"

# --- 하드웨어 설정 ---
serial_port = '/dev/ttyACM0'
baud_rate = 9600

# 라즈베리 파이 SHT31D 센서 및 아두이노 데이터 통신 주기
arduino_interval = 60
raspi_interval = 10

# ⭐ GPIO 핀 설정 ⭐
# 릴레이 모듈은 active_high=False (0값 주면 켜짐, 1값 주면 꺼짐)
MODULE_PINS = {
    "coolerA": 26,
    "coolerB": 19,
    "heater": 13,
    "waterPump": 5,
    "led": 6
}

# GPIO 출력 장치 객체 생성
def setup_gpios():
    GPIO.setmode(GPIO.BCM)
    for pin in MODULE_PINS.values():
        GPIO.setup(pin, GPIO.OUT)
        # 핀을 HIGH로 설정하여 모든 모듈을 꺼진 상태로 시작합니다.
        GPIO.output(pin, GPIO.LOW)
        print(f"GPIO pin {pin} is set up.")


# SHT31D 센서 설정
try:
    i2c = board.I2C()
    sht31d_sensor = adafruit_sht31d.SHT31D(i2c)
    print("SHT31D sensor initialized successfully.")
except ValueError as e:
    print(f"SHT31D sensor error: {e}")
    sys.exit()

# SHT31D 센서 데이터 저장을 위한 배열
temp_arr = [0.0] * 6
humi_arr = [0.0] * 6
reading_index = 0

# --- 워터펌프 자동 종료 로직 ---
def turn_off_pump_after_time():
    pump_pin = MODULE_PINS["waterPump"]
    # 릴레이는 HIGH를 주면 꺼집니다.
    GPIO.output(pump_pin, GPIO.LOW)
    print(f" -> waterPump 10초 가동 후 자동 종료. (GPIO {pump_pin})")

# ⭐ 히터 자동 종료 로직 추가 ⭐
def turn_off_heater_after_time():
    heater_pin = MODULE_PINS["heater"]
    # 릴레이는 HIGH를 주면 꺼집니다.
    GPIO.output(heater_pin, GPIO.LOW)
    print(f" -> heater 1분 가동 후 자동 종료. (GPIO {heater_pin})")
    
# --- MQTT 콜백 함수 ---
def on_connect(client, userdata, flags, rc):
    """MQTT 브로커에 연결되었을 때 호출되는 콜백 함수"""
    if rc == 0:
        print(f"Connected to MQTT Broker: {MQTT_BROKER_IP}")
        # ⭐ 구독: 모든 모듈 제어 명령을 받기 위해 와일드카드로 구독
        client.subscribe(f"farm/{DEVICE_ID}/#")
        print(f"Subscribed to topic: farm/{DEVICE_ID}/#")
    else:
        print(f"Failed to connect, return code {rc}")

def on_message(client, userdata, msg):
    """서버로부터 메시지를 받았을 때 실행되는 콜백 함수"""
    try:
        topic = msg.topic
        payload = msg.payload.decode()
        print(f"\n--- Received command from Server ---")
        print(f"Topic: '{topic}', Payload: {payload}")

        # JSON 페이로드 파싱
        data = json.loads(payload)
        command = data.get("command")
        
        # 토픽에서 모듈 이름 추출
        module_name = topic.split('/')[-1]

        # 명령어에 따라 GPIO 핀 제어
        if module_name in MODULE_PINS:
            pin = MODULE_PINS[module_name]

            if module_name == "waterPump":
                if command == "on":
                    # 릴레이는 LOW를 주면 켜집니다.
                    GPIO.output(pin, GPIO.HIGH)
                    print(f" -> waterPump 모듈 활성화. (GPIO {pin})")
                    # 10초 후 turn_off_pump_after_time 함수를 실행하는 타이머 시작
                    pump_timer = threading.Timer(10, turn_off_pump_after_time)
                    pump_timer.start()
                elif command == "off":
                    GPIO.output(pin, GPIO.LOW)
                    print(f" -> waterPump 모듈 비활성화. (GPIO {pin})")
            
            # ⭐ 히터 제어 로직 추가 ⭐
            elif module_name == "heater":
                if command == "on":
                    GPIO.output(pin, GPIO.HIGH)
                    print(f" -> heater 모듈 활성화. (GPIO {pin})")
                    # 1분(60초) 후 turn_off_heater_after_time 함수를 실행하는 타이머 시작
                    heater_timer = threading.Timer(60, turn_off_heater_after_time)
                    heater_timer.start()
                elif command == "off":
                    GPIO.output(pin, GPIO.LOW)
                    print(f" -> heater 모듈 비활성화. (GPIO {pin})")
            
            # 쿨러A, 쿨러B, LED는 타이머가 필요 없으므로 기존 로직 유지
            elif command == "on":
                GPIO.output(pin, GPIO.HIGH)
                print(f" -> {module_name} 모듈이 활성화되었습니다. (GPIO {pin})")
            elif command == "off":
                GPIO.output(pin, GPIO.LOW)
                print(f" -> {module_name} 모듈이 비활성화되었습니다. (GPIO {pin})")
            else:
                print(f" -> 알 수 없는 명령: {command} for {module_name}")
        else:
            print(f" -> 알 수 없는 모듈: {module_name}")

    except Exception as e:
        print(f"Error processing MQTT message: {e}")

# --- 센서 데이터 함수 ---
def get_sht31d_data(sensor_obj):
    """SHT31D 센서에서 온도와 습도 데이터를 읽는 함수"""
    try:
        temp = float(sensor_obj.temperature)
        humi = float(sensor_obj.relative_humidity)
        return temp, humi
    except Exception as e:
        print(f"SHT31D sensor read error: {e}")
        return None, None

def read_arduino_data(ser_obj):
    """아두이노에서 센서 데이터를 읽는 함수"""
    try:
        ser_obj.write(b'S')
        line = ser_obj.readline().decode('utf-8').strip()
        print(f"Received raw data from Arduino: {line}")
        
        sensor_values = line.split(',')
        if len(sensor_values) == 2:
            soil_raw = float(sensor_values[0])
            water_raw = float(sensor_values[1])
            
            soil_percentage = int(max(0, min(100, ((1023 - soil_raw) / 1023) * 100)))
            water_percentage = int(max(0, min(100, (water_raw / 1023) * 100)))
            
            return soil_percentage, water_percentage
        else:
            print(f"Arduino data format error: {line}")
            return None, None
            
    except (ValueError, IndexError, serial.SerialException) as e:
        print(f"Arduino communication error: {e}")
        return None, None

def publish_data(client, soil_p, water_p, temp_avg, humi_avg):
    """센서 데이터를 JSON 형식으로 가공하여 MQTT 브로커로 발행하는 함수"""
    try:
        data = {
            "soilMoisture": soil_p,
            "waterLevel": water_p,
            "temperature": round(temp_avg, 2),
            "humidity": round(humi_avg, 2),
            "raspberryId": DEVICE_ID,
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
        }
        json_data = json.dumps(data)
        
        # ⭐ 발행: 센서 데이터를 서버로 보낼 토픽 이름으로 변경 ⭐
        publish_topic = "datafarm/sensor_data"
        result = client.publish(publish_topic, json_data)
        
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            print(f"데이터 발행 성공: {json_data}")
        else:
            print(f"데이터 발행 실패. return code: {result.rc}")

    except Exception as e:
        print(f"데이터 전송 오류: {e}")

# --- 메인 루프 ---
if __name__ == "__main__":
    # MQTT 클라이언트 설정
    setup_gpios()
    client = mqtt.Client()
    
    client.on_connect = on_connect
    client.on_message = on_message # 메시지 수신 콜백 함수 등록

    try:
        # 브로커에 연결
        client.connect(MQTT_BROKER_IP, MQTT_BROKER_PORT, 60)
        
        # 시리얼 포트 설정 및 연결
        ser = serial.Serial(serial_port, baud_rate, timeout=1)
        print(f"Listening on {serial_port} at {baud_rate} baud...")

        # 비동기 네트워크 루프 시작
        client.loop_start() 

        last_raspi_read_time = time.time()
        last_arduino_read_time = time.time()
        
        while True:
            current_time = time.time()

            # 라즈베리 파이 센서 데이터 읽기 및 배열 저장
            if current_time - last_raspi_read_time >= raspi_interval:
                temp, humi = get_sht31d_data(sht31d_sensor)
                if temp is not None and humi is not None:
                    temp_arr[reading_index] = temp
                    humi_arr[reading_index] = humi
                    reading_index = (reading_index + 1) % 6
                    print(f"Raspi Sensor : Temp: {temp:.2f} C, Humi: {humi:.2f}%")
                last_raspi_read_time = current_time

            # 아두이노 센서 데이터 읽기 및 MQTT 발행
            if current_time - last_arduino_read_time >= arduino_interval:
                print("\n--- Requesting data from Arduino and Publishing... ---")
                
                soil_p, water_p = read_arduino_data(ser)
                
                if soil_p is not None and water_p is not None:
                    temp_avg = sum(temp_arr) / 6
                    humi_avg = sum(humi_arr) / 6
                    
                    print(f"Publishing Data:")
                    print(f"  Soil Moisture: {soil_p}%")
                    print(f"  Water Level: {water_p}%")
                    print(f"  Temp (AVG): {temp_avg:.2f} C")
                    print(f"  Humi (AVG): {humi_avg:.2f}%\n")
                    
                    publish_data(client, soil_p, water_p, temp_avg, humi_avg)
                
                last_arduino_read_time = current_time

            time.sleep(1)

    except KeyboardInterrupt:
        print("\n스크립트 종료")
    finally:
        if 'ser' in locals() and ser.is_open:
            ser.close()
            print("Serial port closed.")
            
        client.loop_stop()
        client.disconnect()
        print("MQTT client disconnected.")
        sys.exit()
