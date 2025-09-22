# -*- coding: utf-8 -*-

import paho.mqtt.client as mqtt
import json
import time
import sys
import serial
import board
import adafruit_sht31d

# --- MQTT 설정 ---
# TODO: GCP Mosquitto 브로커 VM의 실제 외부 IP 주소로 변경하세요.
MQTT_BROKER_IP = "p"
MQTT_BROKER_PORT = 1883
# TODO: 센서 데이터를 보낼 토픽 이름으로 변경하세요.
MQTT_TOPIC = "plant/sensor_data"

# --- 하드웨어 설정 ---
serial_port = '/dev/ttyACM0'
baud_rate = 9600

# 라즈베리 파이 SHT31D 센서 및 아두이노 데이터 통신 주기
arduino_interval = 60
raspi_interval = 10

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

# --- MQTT 콜백 함수 ---
def on_connect(client, userdata, flags, rc):
    """MQTT 브로커에 연결되었을 때 호출되는 콜백 함수"""
    if rc == 0:
        print(f"Connected to MQTT Broker: {MQTT_BROKER_IP}")
    else:
        print(f"Failed to connect, return code {rc}")

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
            "raspberryId": "raspi-01",
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
        }
        json_data = json.dumps(data)
        
        result = client.publish(MQTT_TOPIC, json_data)
        
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            print(f"데이터 발행 성공: {json_data}")
        else:
            print(f"데이터 발행 실패. return code: {result.rc}")

    except Exception as e:
        print(f"데이터 전송 오류: {e}")

# --- 메인 루프 ---
if __name__ == "__main__":
    # MQTT 클라이언트 설정
    client = mqtt.Client()
    client.on_connect = on_connect

    # 브로커에 연결
    try:
        client.connect(MQTT_BROKER_IP, MQTT_BROKER_PORT, 60)
        client.loop_start() # 백그라운드 스레드에서 네트워크 루프 시작
    except Exception as e:
        print(f"MQTT connection error: {e}")
        sys.exit()

    # 시리얼 포트 설정 및 연결
    try:
        ser = serial.Serial(serial_port, baud_rate, timeout=1)
        print(f"Listening on {serial_port} at {baud_rate} baud...")
    except serial.SerialException as e:
        print(f"Error: {e}")
        print("Check the port name and permissions.")
        sys.exit()

    last_raspi_read_time = time.time()
    last_arduino_read_time = time.time()
    
    try:
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
                    # 라즈베리 파이 센서 평균값 계산
                    temp_avg = sum(temp_arr) / 6
                    humi_avg = sum(humi_arr) / 6
                    
                    print(f"Publishing Data:")
                    print(f"  Soil Moisture: {soil_p}%")
                    print(f"  Water Level: {water_p}%")
                    print(f"  Temp (AVG): {temp_avg:.2f} C")
                    print(f"  Humi (AVG): {humi_avg:.2f}%\n")
                    
                    # MQTT 발행
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
