# -*- coding: utf-8 -*-
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish
from picamera2 import Picamera2
from google.cloud import storage
import json
import time
import sys
import os
import uuid
import threading
import serial
import board
import adafruit_sht31d
import RPi.GPIO as GPIO

# conf
BUCKET_NAME = ""
DEVICE_ID = ""
FARM_ID = ""
CAMERA_ID = ""

# MQTT Broker Info
MQTT_BROKER_HOSTNAME = ""
MQTT_BROKER_PORT = 
MQTT_CONTROL_TOPIC = ""
MQTT_MODULE_CONTROL_TOPIC = ""
MQTT_SENSOR_DATA_TOPIC = ""


# Raspberry Pi Camera
picam2 = Picamera2()
camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
picam2.configure(camera_config)
camera_lock = threading.Lock()

# SHT31D Sensor
try:
    i2c = board.I2C()
    sht31d_sensor = adafruit_sht31d.SHT31D(i2c)
    print("SHT31D sensor initialized successfully.")
except ValueError as e:
    print(f"SHT31D sensor error: {e}")
    sys.exit()

# Arduino Serial
serial_port = '/dev/ttyACM0'
baud_rate = 9600
try:
    ser = serial.Serial(serial_port, baud_rate, timeout=1)
    print(f"Listening on {serial_port} at {baud_rate} baud...")
except serial.SerialException as e:
    print(f"Failed to open serial port {serial_port}: {e}")
    sys.exit()

# GPIO Pins
MODULE_PINS = {
    "coolerA": 26,
    "coolerB": 19,
    "heater": 13,
    "waterPump": 5,
    "led": 6 # LED 핀
}

# 💡 NEW: LED 및 촬영 타이머 설정 상수
LED_DURATION_SEC = 180  # 3분
CAPTURE_DELAY_SEC = 60  # 1분

# Sensor Data Storage
temp_arr = [0.0] * 6
humi_arr = [0.0] * 6
reading_index = 0
last_raspi_read_time = 0
last_arduino_read_time = 0
raspi_interval = 10
arduino_interval = 60

# --- Functions ---

def setup_gpios():
    """Configures GPIO pins for the relay modules."""
    GPIO.setmode(GPIO.BCM)
    for pin in MODULE_PINS.values():
        GPIO.setup(pin, GPIO.OUT)
        # Set all modules to OFF (LOW for these relays, assuming HIGH is ON)
        # 💡 NOTE: LOW는 비활성화(꺼짐), HIGH는 활성화(켜짐)로 통일합니다.
        GPIO.output(pin, GPIO.LOW) 
        print(f"GPIO pin {pin} is set up.")

def get_next_id():
    """Reads, increments, and returns the next photo ID from a file."""
    id_file_path = "picture_id.txt"
    current_id = 0
    if os.path.exists(id_file_path):
        with open(id_file_path, "r") as f:
            try:
                current_id = int(f.read().strip())
            except ValueError:
                current_id = 0
    next_id = current_id + 1
    with open(id_file_path, "w") as f:
        f.write(str(next_id))
    return next_id

def capture_and_upload():
    """Captures a photo, saves it locally, and uploads it to Google Cloud Storage."""
    with camera_lock:
        try:
            picam2.start()
            time.sleep(2) # 카메라 센서 안정화 대기

            directory_path = ""
            os.makedirs(directory_path, exist_ok=True)
            next_id = get_next_id()
            file_name = f""
            file_path = os.path.join(directory_path, file_name)

            picam2.capture_file(file_path)
            print(f"Photo successfully taken and saved locally: {file_path}")

            print("")
            client = storage.Client()
            bucket = client.bucket(BUCKET_NAME)
            blob = bucket.blob(file_name)
            blob.upload_from_filename(file_path)

            public_url = f"{BUCKET_NAME}/{file_name}"
            print(f"GCS upload successful, public URL: {public_url}")

            # 💡 NOTE: GCS 업로드 완료 후 서버에 알림 (Topic/Payload는 설정값으로 채워야 함)
            # mqtt_client.publish("", public_url) 
            # print(f"MQTT message published: Topic '', Payload '{public_url}'")

        except Exception as e:
            print(f"An error occurred during photo capture/upload: {e}", file=sys.stderr)
        finally:
            picam2.stop()
            print("Camera resource successfully stopped.")

# 💡 NEW: LED 타이머 종료 함수
def turn_off_led_after_time():
    """Turns off the LED after the specified duration."""
    led_pin = MODULE_PINS["led"]
    GPIO.output(led_pin, GPIO.LOW)
    print(f" -> led turned off automatically after {LED_DURATION_SEC} seconds. (GPIO {led_pin})")

# 💡 NEW: LED 켜고 지연 후 촬영하는 통합 함수
def led_on_then_capture():
    """Turns on LED, waits, captures photo, and sets a timer to turn LED off."""
    led_pin = MODULE_PINS["led"]
    
    # 1. LED 켜기 (상태와 무관하게 켜기)
    if GPIO.input(led_pin) == GPIO.LOW:
        GPIO.output(led_pin, GPIO.HIGH)
        print(f"LED activated. (GPIO {led_pin})")

    # 2. LED 끄기 타이머 시작 (180초 후 실행)
    led_timer = threading.Timer(LED_DURATION_SEC, turn_off_led_after_time)
    led_timer.start()
    print(f"LED turn-off timer started for {LED_DURATION_SEC} seconds.")

    # 3. 사진 촬영까지 대기 (60초)
    print(f"Waiting for {CAPTURE_DELAY_SEC} seconds before capturing photo...")
    time.sleep(CAPTURE_DELAY_SEC)

    # 4. 사진 촬영 및 업로드
    print("Executing delayed photo capture...")
    capture_and_upload()

def get_sht31d_data(sensor_obj):
# ... (기존 get_sht31d_data 함수 유지)
# ...
    try:
        temp = float(sensor_obj.temperature)
        humi = float(sensor_obj.relative_humidity)
        return temp, humi
    except Exception as e:
        print(f"SHT31D sensor read error: {e}")
        return None, None

def read_arduino_data(ser_obj):
# ... (기존 read_arduino_data 함수 유지)
# ...
    try:
        ser_obj.write(b'S')
        line = ser_obj.readline().decode('utf-8').strip()
        print(f"Received raw data from Arduino: {line}")
        
        sensor_values = line.split(',')
        if len(sensor_values) == 2:
            soil_raw = float(sensor_values[0])
            water_raw = float(sensor_values[1])

            soil_percentage = soil_raw
            water_percentage = water_raw* 2.0
            
            return soil_percentage, water_percentage
        else:
            print(f"Arduino data format error: {line}")
            return None, None
    except (ValueError, IndexError, serial.SerialException) as e:
        print(f"Arduino communication error: {e}")
        return None, None

def publish_data(client, soil_p, water_p, temp_avg, humi_avg):
# ... (기존 publish_data 함수 유지)
# ...
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
        
        result = client.publish(MQTT_SENSOR_DATA_TOPIC, json_data)
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            print(f"Data published successfully: {json_data}")
        else:
            print(f"Failed to publish data. Return code: {result.rc}")
    except Exception as e:
        print(f"Data publishing error: {e}")

def turn_off_pump_after_time():
# ... (기존 turn_off_pump_after_time 함수 유지)
# ...
    """Turns off the water pump after a delay."""
    pump_pin = MODULE_PINS["waterPump"]
    GPIO.output(pump_pin, GPIO.LOW)
    print(f" -> waterPump turned off automatically after 3 seconds. (GPIO {pump_pin})")

def turn_off_heater_after_time():
# ... (기존 turn_off_heater_after_time 함수 유지)
# ...
    """Turns off the heater after a delay."""
    heater_pin = MODULE_PINS["heater"]
    GPIO.output(heater_pin, GPIO.LOW)
    print(f" -> heater turned off automatically after 60 seconds. (GPIO {heater_pin})")

# --- MQTT Callbacks ---

def on_connect(client, userdata, flags, rc):
# ... (기존 on_connect 함수 유지)
# ...
    """Callback for when the client connects to the MQTT broker."""
    if rc == 0:
        print(f"Connected to MQTT Broker: {MQTT_BROKER_HOSTNAME}")
        client.subscribe(MQTT_CONTROL_TOPIC)
        client.subscribe(MQTT_MODULE_CONTROL_TOPIC)
        print(f"Subscribed to topics: '{MQTT_CONTROL_TOPIC}' and '{MQTT_MODULE_CONTROL_TOPIC}'")
    else:
        print(f"Failed to connect, return code {rc}")

def on_message(client, userdata, msg):
    """Callback for when a message is received from the broker."""
    print(f"\n--- Received command from Server ---")
    print(f"Topic: '{msg.topic}', Payload: {msg.payload.decode()}")

    if msg.topic == MQTT_CONTROL_TOPIC and msg.payload.decode() == "capture":
        print("Executing LED ON, 60s delay, then photo capture command...")
        # 💡 CHANGE: 별도의 스레드로 새로운 함수 실행하여 메인 루프를 막지 않도록 처리
        capture_thread = threading.Thread(target=led_on_then_capture)
        capture_thread.start()
        return

    try:
        data = json.loads(msg.payload.decode())
        command = data.get("command")
        module_name = msg.topic.split('/')[-1]

        if module_name in MODULE_PINS:
            pin = MODULE_PINS[module_name]

            if command == "on":
                GPIO.output(pin, GPIO.HIGH)
                print(f" -> {module_name} module activated. (GPIO {pin})")
                if module_name == "waterPump":
                    pump_timer = threading.Timer(3, turn_off_pump_after_time)
                    pump_timer.start()
                # 💡 NOTE: LED는 on_message에서 일반적인 'on' 명령으로 켜지면 타이머를 설정하지 않습니다.
                # 'capture' 명령을 통해서만 180초 타이머가 적용됩니다.
                elif module_name == "heater":
                    # 💡 NOTE: 120초(2분) 타이머 대신 60초(1분)로 수정 (기존 코드의 turn_off_heater_after_time 함수 참조)
                    heater_timer = threading.Timer(60, turn_off_heater_after_time) 
                    heater_timer.start()
            elif command == "off":
                GPIO.output(pin, GPIO.LOW)
                print(f" -> {module_name} module deactivated. (GPIO {pin})")
            else:
                print(f" -> Unknown command: {command} for {module_name}")
        else:
            print(f" -> Unknown module: {module_name}")

    except Exception as e:
        print(f"Error processing MQTT message: {e}")

# --- Main Execution ---
if __name__ == '__main__':
    setup_gpios()
    
    mqtt_client = mqtt.Client(client_id=str(uuid.uuid4()))
    mqtt_client.on_connect = on_connect
    mqtt_client.on_message = on_message
    
    try:
        mqtt_client.connect(MQTT_BROKER_HOSTNAME, MQTT_BROKER_PORT, 60)
        mqtt_client.loop_start()

        last_raspi_read_time = time.time()
        last_arduino_read_time = time.time()

        while True:
            current_time = time.time()

            # Read Raspberry Pi sensors
            if current_time - last_raspi_read_time >= raspi_interval:
                temp, humi = get_sht31d_data(sht31d_sensor)
                if temp is not None and humi is not None:
                    temp_arr[reading_index] = temp
                    humi_arr[reading_index] = humi
                    reading_index = (reading_index + 1) % 6
                    print(f"Raspi Sensor: Temp: {temp:.2f} C, Humi: {humi:.2f}%")
                last_raspi_read_time = current_time

            # Read Arduino sensors and publish data
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
                    
                    publish_data(mqtt_client, soil_p, water_p, temp_avg, humi_avg)
                
                last_arduino_read_time = current_time

            time.sleep(1)

    except KeyboardInterrupt:
        print("\nScript terminated by user.")
    finally:
        if 'ser' in locals() and ser.is_open:
            ser.close()
            print("Serial port closed.")
        
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        print("MQTT client disconnected.")
        GPIO.cleanup()
        print("GPIO cleaned up.")
        sys.exit()
