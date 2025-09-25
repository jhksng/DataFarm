from picamera2 import Picamera2
from google.cloud import storage
import paho.mqtt.publish as publish
import time
import os
import sys
from flask import Flask, jsonify, request

# --- 설정 ---
RASPI_ID = "raspi-01"
BUCKET_NAME = "datafarm-picture"
MQTT_BROKER_HOSTNAME = "YOUR_MQTT_BROKER_HOSTNAME"
ID_FILE_PATH = "picture_id.txt"

# Flask 애플리케이션 초기화
app = Flask(__name__)

# --- 순차 ID 관리 함수 ---
def get_next_id():
    """ID 파일을 읽고, 1을 더한 후 다시 저장하여 순차적인 ID를 반환합니다."""
    try:
        if os.path.exists(ID_FILE_PATH):
            with open(ID_FILE_PATH, 'r') as f:
                current_id = int(f.read().strip())
        else:
            current_id = 0
    except (IOError, ValueError):
        current_id = 0

    next_id = current_id + 1
    with open(ID_FILE_PATH, 'w') as f:
        f.write(str(next_id))
        
    return next_id

# --- 사진 촬영 및 업로드 함수 ---
def capture_and_upload():
    try:
        # Picamera2 초기화
        picam2 = Picamera2()
        camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
        picam2.configure(camera_config)
        picam2.set_controls({
            "AfMode": 0,
            "LensPosition": 1
        })
        picam2.start()
        time.sleep(2)  # 카메라 안정화

        # 저장 디렉토리
        directory_path = "/home/pi/datafarm/picture"
        os.makedirs(directory_path, exist_ok=True)

        # 파일 이름 생성 (raspi-01/id/시간)
        next_id = get_next_id()
        id_folder_name = f"{RASPI_ID}/{next_id}"
        time_part = time.strftime('%Y-%m-%d_%H-%M-%S')
        file_name = f"{id_folder_name}/photo_{time_part}.jpg"
        file_path = os.path.join(directory_path, f"{next_id}_{time_part}.jpg") # Local file name

        # 사진 촬영 및 저장
        picam2.capture_file(file_path)
        picam2.stop()

        print(f"사진이 성공적으로 촬영되어 로컬에 저장되었습니다: {file_path}")

        # GCS에 사진 업로드
        print(f"GCS 버킷 '{BUCKET_NAME}'에 사진을 업로드하는 중...")
        client = storage.Client()
        bucket = client.bucket(BUCKET_NAME)
        # GCS blob 이름은 로컬 경로와 다르게 순차 ID를 포함하는 디렉토리 구조를 사용합니다.
        blob = bucket.blob(file_name)
        blob.upload_from_filename(file_path)
        print(f"GCS 업로드 성공: gs://{BUCKET_NAME}/{file_name}")

        # MQTT 메시지 발행
        print(f"MQTT 브로커 '{MQTT_BROKER_HOSTNAME}'에 메시지를 발행하는 중...")
        publish.single("photo/uploaded", file_name, hostname=MQTT_BROKER_HOSTNAME)
        print(f"MQTT 메시지 발행 성공: 토픽 'photo/uploaded', 페이로드 '{file_name}'")

        return {"message": "사진 촬영 및 업로드 성공", "file_name": file_name}, 200

    except Exception as e:
        print(f"오류가 발생했습니다: {e}", file=sys.stderr)
        return {"error": f"오류 발생: {str(e)}"}, 500

# --- API 엔드포인트 ---
@app.route('/take-photo', methods=['GET'])
def take_photo():
    """스프링 서버의 요청을 받아 사진을 촬영하고 GCS에 업로드합니다."""
    response, status_code = capture_and_upload()
    return jsonify(response), status_code

# --- 서버 실행 ---
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
