# gcs_api_publisher.py (Python Script on Raspberry Pi)

from picamera2 import Picamera2
from google.cloud import storage
import paho.mqtt.publish as publish
import time
import os
import sys
from flask import Flask, jsonify, request
import threading

# --- Configuration ---
FARM_ID = "farm01"
CAMERA_ID = "cam01"
BUCKET_NAME = "datafarm-picture"
MQTT_BROKER_HOSTNAME = "YOUR_MQTT_BROKER_HOSTNAME"

# Flask application initialization
app = Flask(__name__)

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
    try:
        # Picamera2 initialization
        picam2 = Picamera2()
        camera_config = picam2.create_still_configuration(main={"size": (4056, 3040)})
        picam2.configure(camera_config)
        picam2.set_controls({
            "AfMode": 0,
            "LensPosition": 1
        })
        picam2.start()
        time.sleep(2)  # Camera stabilization

        # Save directory
        directory_path = "/home/pi/datafarm/picture"
        os.makedirs(directory_path, exist_ok=True)

        # Generate file name with sequential ID
        next_id = get_next_id()
        file_name = f"{FARM_ID}_{CAMERA_ID}_{next_id}_{time.strftime('%Y-%m-%d_%H-%M-%S')}.jpg"
        file_path = os.path.join(directory_path, file_name)

        # Capture and save photo
        picam2.capture_file(file_path)
        picam2.stop()

        print(f"Photo successfully taken and saved locally: {file_path}")

        # Upload photo to GCS
        print(f"Uploading photo to GCS bucket '{BUCKET_NAME}'...")
        client = storage.Client()
        bucket = client.bucket(BUCKET_NAME)
        blob = bucket.blob(file_name)
        blob.upload_from_filename(file_path)

        # Get the public URL for the uploaded photo
        # Note: This requires the bucket to be publicly readable or requires signed URLs
        public_url = f"https://storage.googleapis.com/{BUCKET_NAME}/{file_name}"
        print(f"GCS upload successful, public URL: {public_url}")

        # Publish public URL to MQTT
        print(f"Publishing MQTT message to broker '{MQTT_BROKER_HOSTNAME}'...")
        publish.single("photo/uploaded", public_url, hostname=MQTT_BROKER_HOSTNAME)
        print(f"MQTT message published successfully: Topic 'photo/uploaded', Payload '{public_url}'")

        return {"message": "Photo taken and uploaded successfully", "public_url": public_url}, 200

    except Exception as e:
        print(f"An error occurred: {e}", file=sys.stderr)
        return {"error": f"An error occurred: {str(e)}"}, 500

# --- API Endpoints ---
@app.route('/take-photo', methods=['GET'])
def take_photo():
    """Takes a photo and uploads it to GCS upon request from the Spring server."""
    response, status_code = capture_and_upload()
    return jsonify(response), status_code

# --- Automated Photo Schedule Function ---
def schedule_photo():
    """Schedules a photo to be taken and uploaded every hour."""
    while True:
        print("Scheduled photo capture is about to start...")
        capture_and_upload()
        # Sleep for 1 hour (3600 seconds)
        time.sleep(3600)

# --- Server execution ---
if __name__ == '__main__':
    # Start the scheduled photo capture in a separate thread
    scheduled_thread = threading.Thread(target=schedule_photo)
    scheduled_thread.daemon = True  # Allows the main program to exit even if this thread is still running
    scheduled_thread.start()

    # Run the Flask app, accessible from outside the container
    app.run(host='0.0.0.0', port=5000, debug=True)
