from picamera2 import Picamera2
from google.cloud import storage
import paho.mqtt.publish as publish
import datetime
import time
import os


# 사진 촬영
picam2 = Picamera2()
picam2.options['preview'] = 'null'
picam2.set_controls({"AfMode":0, "LensPosition": 1.0})
picam2.start()
time.sleep(2)
file_name = f"photo_{time.strftime('%Y-%m-%d_%H-%M-%S')}.jpg"

file_path = f"images/{file_name}"

#picam2.capture('image.jpg')
picam2.capture_file(file_path)
picam2.stop_preview()

# GCS에 사진 업로드
client = storage.Client()
bucket = client.bucket("") # 버킷 이름
blob = bucket.blob(file_name)
blob.upload_from_filename(file_path)

# MQTT 메시지 발행
gcs_url = f"gs:///{file_name}"
print(f"File uploaded to GCS: {gcs_url}")

# 스프링 서버에 URL을 MQTT 메시지로 보냅니다.
# 라즈베리파이가 있는 네트워크에 따라 브로커 주소를 다르게 설정해야 할 수 있습니다.
# 같은 네트워크일 경우 브로커 IP 주소, 다른 네트워크일 경우 공인 IP 주소를 사용하세요.
publish.single("photo/uploaded", gcs_url, hostname="")
