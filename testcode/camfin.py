from picamera2 import Picamera2
import time
import os

# --- Picamera2 초기화 ---
picam2 = Picamera2()

# 최대 해상도로 촬영 (모듈3 기본 센서 해상도)
camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
picam2.configure(camera_config)

picam2.set_controls({
	"AfMode": 0,
	"LensPosition": 1
})

picam2.start()
time.sleep(2)  # 카메라 안정화

# --- 저장 디렉토리 ---
directory_path = "/home/pi/datafarm/picture"
os.makedirs(directory_path, exist_ok=True)

# --- 파일 이름 생성 ---
file_name = f"photo_{time.strftime('%Y-%m-%d_%H-%M-%S')}.jpg"
file_path = os.path.join(directory_path, file_name)

# --- 사진 촬영 ---
picam2.capture_file(file_path)
picam2.stop()

print(f"저장: {file_path}")
