const int soil_pin = A0;
const int water_pin = A1;
const long time = 10000; //10 sec

int count = 0;
unsigned long ReadTime = 0;

float soil_arr[6];
float water_arr[6];

void setup(){
	Serial.begin(9600);
	pinMode(A0, INPUT);
	pinMode(A1, INPUT);
}

void loop(){

	if (millis() - ReadTime >= time){
		ReadTime = millis();
		
		int soil_v = analogRead(soil_pin);
		int water_v = analogRead(water_pin);

		soil_arr[count] = (float)soil_v;
		water_arr[count] = (float)water_v;
		count++;

		if(count >= 6)
			count = 0;
	} 

	if (Serial.available() > 0){
		char command = Serial.read();
		if (command == 'S'){
			float soil_avg = 0;
			float water_avg = 0;
		
			for(int i = 0; i < 6; i++){
				soil_avg += soil_arr[i];
				water_avg += water_arr[i];
			}
			
			soil_avg /= 6.0;
			water_avg /= 6.0;

			Serial.print(soil_avg, 2);
			Serial.print(",");
			Serial.println(water_avg, 2);
		}
	}
}
