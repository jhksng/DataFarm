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
import cv2
import numpy as np
import logging
from datetime import datetime, timezone

# --- Logging Configuration ---
LOG_FILE = "/home/pi/datafarm/system_log.txt"
os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.FileHandler(LOG_FILE, encoding="utf-8"),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

# --- Configuration ---
BUCKET_NAME = "datafarm-picture"
DEVICE_ID = "raspi-01"
FARM_ID = "farm01"
CAMERA_ID = "cam01"

# MQTT Settings
MQTT_BROKER_HOSTNAME = ""
MQTT_BROKER_PORT = 1883
MQTT_CONTROL_TOPIC = "farm/raspi-01/camera-command"
MQTT_MODULE_CONTROL_TOPIC = f"farm/{DEVICE_ID}/#"
MQTT_SENSOR_DATA_TOPIC = "datafarm/sensor_data"
MQTT_ANALYSIS_TOPIC = "analysis/result"

# Raspberry Pi Camera
picam2 = Picamera2()
camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
picam2.configure(camera_config)
camera_lock = threading.Lock()

# SHT31D Sensor
try:
    i2c = board.I2C()
    sht31d_sensor = adafruit_sht31d.SHT31D(i2c)
    logger.info("SHT31D sensor initialized successfully.")
except ValueError as e:
    logger.error(f"SHT31D sensor initialization failed: {e}")
    sys.exit()

# Arduino Serial
serial_port = '/dev/ttyACM0'
baud_rate = 9600
try:
    ser = serial.Serial(serial_port, baud_rate, timeout=1)
    logger.info(f"Serial connection established on {serial_port} at {baud_rate} baud.")
except serial.SerialException as e:
    logger.error(f"Failed to open serial port {serial_port}: {e}")
    sys.exit()

# GPIO Pin Configuration
MODULE_PINS = {
    "coolerA": 26,
    "coolerB": 19,
    "heater": 13,
    "waterPump": 5,
    "led": 6
}

LED_DURATION_SEC = 60
CAPTURE_DELAY_SEC = 20

temp_arr = [0.0] * 6
humi_arr = [0.0] * 6
reading_index = 0
last_raspi_read_time = 0
last_arduino_read_time = 0
raspi_interval = 10
arduino_interval = 60

def setup_gpios():
    GPIO.setmode(GPIO.BCM)
    for pin in MODULE_PINS.values():
        GPIO.setup(pin, GPIO.OUT)
        GPIO.output(pin, GPIO.LOW)
        logger.info(f"GPIO pin {pin} initialized (OFF).")

def get_next_id():
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

def analyze_image_and_publish(client, file_name, file_path):
    try:
        img = cv2.imread(file_path)
        if img is None:
            logger.warning(f"Image '{file_path}' could not be loaded.")
            return

        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        lower_bound = np.array([140, 50, 50])
        upper_bound = np.array([180, 255, 255])
        mask = cv2.inRange(hsv, lower_bound, upper_bound)
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        total_area = sum(cv2.contourArea(c) for c in contours)
        total_pixels = img.shape[0] * img.shape[1]
        bright_ratio = (total_area / total_pixels) * 100

        logger.info(f"Image analysis for {file_name}: bright ratio = {bright_ratio:.4f}%")

        with open('brightness_result.txt', 'a', encoding='utf-8') as f:
            f.write(f"{file_name}:{bright_ratio:.4f}%\n")

        payload = json.dumps({
            "fileName": file_name,
            "brightnessRatio": float(f"{bright_ratio:.4f}")
        })
        result = client.publish(MQTT_ANALYSIS_TOPIC, payload)
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            logger.info(f"MQTT publish success: {payload}")
        else:
            logger.error(f"MQTT publish failed (code: {result.rc})")
    except Exception as e:
        logger.error(f"Error during image analysis: {e}")

def capture_and_upload():
    with camera_lock:
        try:
            picam2.start()
            time.sleep(2)
            directory_path = "/home/pi/datafarm/picture"
            os.makedirs(directory_path, exist_ok=True)
            next_id = get_next_id()
            file_name = f"{FARM_ID}_{CAMERA_ID}_{next_id}_{time.strftime('%Y%m%d_%H%M%S', time.gmtime())}.jpg"
            file_path = os.path.join(directory_path, file_name)

            picam2.capture_file(file_path)
            logger.info(f"Photo captured and saved: {file_path}")

            client = storage.Client()
            bucket = client.bucket(BUCKET_NAME)
            blob = bucket.blob(file_name)
            blob.upload_from_filename(file_path)

            public_url = f"{BUCKET_NAME}/{file_name}"
            logger.info(f"GCS upload successful: {public_url}")

            mqtt_client.publish("photo/uploaded", public_url)
            analyze_image_and_publish(mqtt_client, file_name, file_path)
        except Exception as e:
            logger.error(f"Error during capture/upload: {e}")
        finally:
            picam2.stop()
            logger.info("Camera stopped.")

def turn_off_led_after_time():
    led_pin = MODULE_PINS["led"]
    GPIO.output(led_pin, GPIO.LOW)
    logger.info(f"LED turned off automatically after {LED_DURATION_SEC} seconds.")

def led_on_then_capture():
    led_pin = MODULE_PINS["led"]
    GPIO.output(led_pin, GPIO.HIGH)
    logger.info("LED turned ON for photo capture preparation.")

    threading.Timer(LED_DURATION_SEC, turn_off_led_after_time).start()
    logger.info(f"LED timer started for {LED_DURATION_SEC} seconds.")
    time.sleep(CAPTURE_DELAY_SEC)
    logger.info("Capturing photo after delay.")
    capture_and_upload()

def get_sht31d_data(sensor_obj):
    try:
        temp = float(sensor_obj.temperature)
        humi = float(sensor_obj.relative_humidity)
        return temp, humi
    except Exception as e:
        logger.error(f"SHT31D read error: {e}")
        return None, None

def read_arduino_data(ser_obj):
    try:
        ser_obj.write(b'S')
        line = ser_obj.readline().decode('utf-8').strip()
        logger.info(f"Raw data from Arduino: {line}")
        sensor_values = line.split(',')
        if len(sensor_values) == 2:
            soil_raw = float(sensor_values[0])
            water_raw = float(sensor_values[1])
            return soil_raw, water_raw * 2.0
        else:
            logger.warning(f"Invalid Arduino data format: {line}")
            return None, None
    except Exception as e:
        logger.error(f"Arduino communication error: {e}")
        return None, None

def publish_data(client, soil_p, water_p, temp_avg, humi_avg):
    try:
        data = {
            "soilMoisture": soil_p,
            "waterLevel": water_p,
            "temperature": round(temp_avg, 2),
            "humidity": round(humi_avg, 2),
            "raspberryId": DEVICE_ID,
            "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        }
        json_data = json.dumps(data)
        result = client.publish(MQTT_SENSOR_DATA_TOPIC, json_data)
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            logger.info(f"MQTT data publish success: {json_data}")
        else:
            logger.error(f"MQTT data publish failed (code: {result.rc})")
    except Exception as e:
        logger.error(f"Error publishing sensor data: {e}")

def turn_off_pump_after_time():
    pump_pin = MODULE_PINS["waterPump"]
    GPIO.output(pump_pin, GPIO.LOW)
    logger.info("Water pump turned off automatically after 3 seconds.")

def turn_off_heater_after_time():
    heater_pin = MODULE_PINS["heater"]
    GPIO.output(heater_pin, GPIO.LOW)
    logger.info("Heater turned off automatically after 60 seconds.")

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logger.info("Connected to MQTT broker.")
        client.subscribe(MQTT_CONTROL_TOPIC)
        client.subscribe(MQTT_MODULE_CONTROL_TOPIC)
    else:
        logger.error(f"MQTT connection failed (code: {rc})")

def on_message(client, userdata, msg):
    logger.info(f"Received MQTT message - Topic: {msg.topic}, Payload: {msg.payload.decode()}")

    if msg.topic == MQTT_CONTROL_TOPIC and msg.payload.decode() == "capture":
        logger.info("Capture command received: turning on LED and preparing photo.")
        threading.Thread(target=led_on_then_capture).start()
        return

    try:
        data = json.loads(msg.payload.decode())
        command = data.get("command")
        module_name = msg.topic.split('/')[-1]
        if module_name in MODULE_PINS:
            pin = MODULE_PINS[module_name]
            if command == "on":
                GPIO.output(pin, GPIO.HIGH)
                logger.info(f"{module_name} turned ON (GPIO {pin})")
                if module_name == "waterPump":
                    threading.Timer(3, turn_off_pump_after_time).start()
                elif module_name == "heater":
                    threading.Timer(60, turn_off_heater_after_time).start()
            elif command == "off":
                GPIO.output(pin, GPIO.LOW)
                logger.info(f"{module_name} turned OFF (GPIO {pin})")
        else:
            logger.warning(f"Unknown module: {module_name}")
    except Exception as e:
        logger.error(f"Error processing MQTT message: {e}")

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
            if current_time - last_raspi_read_time >= raspi_interval:
                temp, humi = get_sht31d_data(sht31d_sensor)
                if temp is not None and humi is not None:
                    temp_arr[reading_index] = temp
                    humi_arr[reading_index] = humi
                    reading_index = (reading_index + 1) % 6
                    logger.info(f"SHT31D: Temp={temp:.2f}°C, Humi={humi:.2f}%")
                last_raspi_read_time = current_time

            if current_time - last_arduino_read_time >= arduino_interval:
                logger.info("Requesting data from Arduino...")
                soil_p, water_p = read_arduino_data(ser)
                if soil_p is not None and water_p is not None:
                    temp_avg = sum(temp_arr) / 6
                    humi_avg = sum(humi_arr) / 6
                    publish_data(mqtt_client, soil_p, water_p, temp_avg, humi_avg)
                last_arduino_read_time = current_time
            time.sleep(1)

    except KeyboardInterrupt:
        logger.warning("Program terminated by user.")
    finally:
        if 'ser' in locals() and ser.is_open:
            ser.close()
            logger.info("Serial port closed.")
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        GPIO.cleanup()
        logger.info("MQTT disconnected and GPIO cleaned up.")
        sys.exit()
