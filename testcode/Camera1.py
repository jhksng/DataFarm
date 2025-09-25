from picamera2 import Picamera2
from google.cloud import storage
import paho.mqtt.publish as publish
import paho.mqtt.client as mqtt
import time
import os
import sys
from flask import Flask, jsonify
import threading
import uuid
import json

# --- Configuration ---
FARM_ID = "farm01"
CAMERA_ID = "cam01"
BUCKET_NAME = "datafarm-picture"
MQTT_BROKER_HOSTNAME = "34.64.167.185"
MQTT_CONTROL_TOPIC = "farm/raspi-01/camera-command"

# Flask application initialization
app = Flask(__name__)

# ✅ 전역에서 카메라 객체 생성 및 초기화
# 이 시점에서 카메라 하드웨어가 한 번만 준비됩니다.
picam2 = Picamera2()
camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
picam2.configure(camera_config)

# ✅ 전역 변수로 Lock 객체 생성
camera_lock = threading.Lock()

# --- Function to get the next photo ID ---
def get_next_id():
    """Reads the next photo ID from a file, increments it, and saves it back."""
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

# --- Photo capture and upload function ---
def capture_and_upload():
    # ✅ Lock을 사용하여 다른 스레드가 접근하는 것을 막습니다.
    with camera_lock:
        try:
            # ✅ 카메라를 시작하고
            picam2.start()
            time.sleep(2)  # 카메라 안정화
            
            # Save directory
            directory_path = "/home/pi/datafarm/picture"
            os.makedirs(directory_path, exist_ok=True)

            # Generate file name with sequential ID
            next_id = get_next_id()
            file_name = f"{FARM_ID}_{CAMERA_ID}_{next_id}_{time.strftime('%Y-%m-%d_%H-%M-%S')}.jpg"
            file_path = os.path.join(directory_path, file_name)

            # Capture and save photo
            picam2.capture_file(file_path)
            
            print(f"Photo successfully taken and saved locally: {file_path}")

            # Upload photo to GCS
            print(f"Uploading photo to GCS bucket '{BUCKET_NAME}'...")
            client = storage.Client()
            bucket = client.bucket(BUCKET_NAME)
            blob = bucket.blob(file_name)
            blob.upload_from_filename(file_path)

            # Get the public URL for the uploaded photo
            public_url = f"https://storage.googleapis.com/{BUCKET_NAME}/{file_name}"
            print(f"GCS upload successful, public URL: {public_url}")

            # Publish public URL to MQTT
            print(f"Publishing MQTT message to broker '{MQTT_BROKER_HOSTNAME}'...")
            mqtt_client.publish("photo/uploaded", public_url)
            print(f"MQTT message published successfully: Topic 'photo/uploaded', Payload '{public_url}'")

            return {"message": "Photo taken and uploaded successfully", "public_url": public_url}, 200

        except Exception as e:
            print(f"An error occurred: {e}", file=sys.stderr)
            return {"error": f"An error occurred: {str(e)}"}, 500
        finally:
            # ✅ 어떤 상황에서도 카메라를 멈춥니다. close()는 하지 않습니다.
            picam2.stop()
            print("Camera resource successfully stopped.")

# --- API Endpoints ---
@app.route('/take-photo', methods=['GET'])
def take_photo_endpoint():
    """Handles API requests to take a photo."""
    response, status_code = capture_and_upload()
    return jsonify(response), status_code

# --- MQTT Message Handler ---
def on_message(client, userdata, msg):
    print(f"Received command from topic '{msg.topic}': {msg.payload.decode()}")
    if msg.payload.decode() == "capture":
        print("Executing photo capture command...")
        capture_and_upload()

# --- Server execution ---
if __name__ == '__main__':
    # MQTT 클라이언트 설정 및 시작
    mqtt_client = mqtt.Client(client_id=str(uuid.uuid4()))
    mqtt_client.on_message = on_message
    mqtt_client.connect(MQTT_BROKER_HOSTNAME, 1883, 60)
    mqtt_client.subscribe(MQTT_CONTROL_TOPIC)
    mqtt_client.loop_start()

    # Flask 앱 실행
    app.run(host='0.0.0.0', port=5000, debug=True, use_reloader=False)
