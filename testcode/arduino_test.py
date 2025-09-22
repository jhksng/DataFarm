import serial
import time
import board
import adafruit_sht31d
import sys


serial_port = '/dev/ttyACM0'
baud_rate = 9600

arduino_interval = 60
raspi_interval = 10

i2c = board.I2C()

try:
	sht31d_sensor = adafruit_sht31d.SHT31D(i2c)
except ValueError as e:
	print(f"SHT31D sensor error: {e}")
	sys.exit()

temp_arr = [0.0] * 6
humi_arr = [0.0] * 6
reading_index = 0
last_raspi_read_time = time.time()

def getTemp(sensor_obj):
	try:
		return float(sensor_obj.temperature)
	except Exception as e:
		print(f"Temp sensor read error: {e}")
		sys.stdout.flush()
		return 0.0

def getHumi(sensor_obj):
	try:
		return float(sensor_obj.relative_humidity)
	except Exception as e:
		print(f"Humi sensor read error: {e}")
		sys.stdout.flush()
		return 0.0



try:
	ser = serial.Serial(serial_port, baud_rate, timeout=1)
	print(f"Listening on {serial_port} at {baud_rate} baud...")

	last_request_time = time.time()

	while True:
		current_time = time.time()
		
		if current_time - last_raspi_read_time >= raspi_interval:
			last_raspi_read_time = current_time

			temp = getTemp(sht31d_sensor)
			humi = getHumi(sht31d_sensor)

			temp_arr[reading_index] = temp
			humi_arr[reading_index] = humi
			reading_index += 1

			if reading_index >= 6:
				reading_index = 0;

			print(f"Raspi Sensor : Temp: {temp:.2f} C, Humi: {humi:.2f}%")


		if current_time - last_request_time >= arduino_interval:
			print("Requesting average data from Arduino...")
			ser.write(b'S')
			last_request_time = current_time
			
			temp_avg = sum(temp_arr) / len(temp_arr)
			humi_avg = sum(humi_arr) / len(humi_arr)
			
			
			line = ser.readline().decode('utf-8').strip()
			print(f"Received raw data from Arduino: {line}")

			sensor_values = line.split(',')
			
			if len(sensor_values) == 2:
				try:
					soil_avg = float(sensor_values[0])
					water_avg = float(sensor_values[1])

					soil_percentage = ((1023 - soil_avg) / 1023) * 100
					water_percentage = (water_avg / 1023) * 100
					
					soil_percentage = int(max(0, min(100, soil_percentage)))
					water_percentage = int(max(0, min(100, water_percentage)))

					print("---")
					print(f"Soil Moisture (AVG): {soil_percentage}%")
					print(f"Water Level (AVG): {water_percentage}%")
					print(f"Temp (AVG): {temp_avg} C")
					print(f"Humi (AVG): {humi_avg}%")
					print("---")

				except ValueError:
					print("Wrong type data: "+line)
			else:
				print("Data TYPE ERROR: "+line)
		time.sleep(1)
except serial.SerialException as e:
	print(f"Error: {e}")
	print("check the port name and permissions.")
except KeyboardInterrupt:
	print("Program terminated by user.")
finally:
	if 'ser' in locals() and ser.is_open:
		ser.close()
		print("Serial port closed.")
