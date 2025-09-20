from picamera2 import Picamera2
from libcamera import controls

from google.cloud import storage
import paho.mqtt.publish as publish
import datetime
import time
import os

picam2 = Picamera2()
config = picam2.create_still_configuration(main={"size": (1920, 1080)})
picam2.configure(config)

picam2.set_controls({"AfMode": controls.AfModeEnum.Continuous})
picam2.start()

time.sleep(2)


# 사진 촬영
file_name = f'images/photo_{datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")}.jpg'

if not os.path.exists('images'):
	os.makedirs('images')

picam2.capture_file(file_name)

# GCS에 사진 업로드
client = storage.Client()
bucket = client.bucket("") # 버킷 이름
blob = bucket.blob(file_name)
blob.upload_from_filename(file_name)

# MQTT 메시지 발행
gcs_url = f"gs://smartfarm-images-2025/{file_name}"
print(f"File uploaded to GCS: {gcs_url}")

# 스프링 서버에 URL을 MQTT 메시지로 보냅니다.
# 라즈베리파이가 있는 네트워크에 따라 브로커 주소를 다르게 설정해야 할 수 있습니다.
# 같은 네트워크일 경우 브로커 IP 주소, 다른 네트워크일 경우 공인 IP 주소를 사용하세요.
publish.single("photo/uploaded", gcs_url, hostname="10.178.0.3")
