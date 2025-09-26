<h2>Arduino Configuration</h2>


curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh

sudo mv arduino-cli /usr/local/bin/

arduino-cli core update-index
arduino-cli core install arduino:avr


sudo nano ~/.arduino15/arduino-cli.yaml

directories:
  data: /home/pi/.arduino15
  downloads: /home/pi/.arduino15/staging
  user: /home/pi/Arduino

library:
  enable_unsafe_install: false

daemon:
  port: "50051"


컴파일 및 업로드

arduino-cli compile -b arduino:avr:uno [project name, sensor.ino] -u -p [board name, /dev/ttyACM0]
