from picamera2 import Picamera2
import time
import os

# 사진 촬영
picam2 = Picamera2()
picam2.options['preview'] = 'null'
picam2.set_controls({"AfMode":0, "LensPosition": 1.0})
picam2.start()
time.sleep(2)

# 'datafarm/picture' 디렉토리가 없으면 생성
directory_path = "datafarm/picture"
if not os.path.exists(directory_path):
    os.makedirs(directory_path)

# 파일 이름 생성
file_name = f"photo_{time.strftime('%Y-%m-%d_%H-%M-%S')}.jpg"
file_path = os.path.join(directory_path, file_name)

# 사진 캡처 및 저장
picam2.capture_file(file_path)
picam2.stop_preview()

print(f"사진이 성공적으로 촬영되었습니다: {file_path}")
