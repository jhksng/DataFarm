from picamera2 import Picamera2
import time
import os
from PIL import Image

# --- Picamera2 초기화 ---
picam2 = Picamera2()

# 최대 해상도로 촬영 (모듈3 기본 센서 해상도)
camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
picam2.configure(camera_config)

picam2.start()
time.sleep(2)  # 카메라 안정화

# --- 저장 디렉토리 ---
directory_path = "/home/pi/datafarm/picture"
os.makedirs(directory_path, exist_ok=True)
git
# --- 파일 이름 생성 ---
file_name = f"photo_{time.strftime('%Y-%m-%d_%H-%M-%S')}.jpg"
file_path = os.path.join(directory_path, file_name)

# --- 사진 촬영 ---
raw_file_path = os.path.join(directory_path, "raw_" + file_name)
picam2.capture_file(raw_file_path)  # 원본 저장

# --- 이미지 크롭 (멀리서 찍힌 느낌) ---
crop_width, crop_height = 1536, 864  # 원하는 영역 크기
img = Image.open(raw_file_path)
img_width, img_height = img.size

# 이미지 중앙 기준 크롭
left = (img_width - crop_width) // 2
top = (img_height - crop_height) // 2
right = left + crop_width
bottom = top + crop_height

cropped_img = img.crop((left, top, right, bottom))
cropped_img.save(file_path)

picam2.stop()

print(f"원본 사진 저장: {raw_file_path}")
print(f"크롭 후 사진 저장: {file_path}")
