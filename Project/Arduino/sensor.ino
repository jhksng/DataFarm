// 고정 값
const int soil_pin = A0;
const int water_pin = A1;
const long time = 10000; //10 sec

// 토양 습도 센서의 경우 습도가 높으면 아날로그 값이 낮아짐, 수위 감지 센서는 반대
const int soilDry = 1023;
const int soilWet = 300;
const int waterDry = 300;
const int waterWet = 1023;

// 10초 마다 체크 count, 시간 ReadTime
int count = 0;
unsigned long ReadTime = 0;

float soil_arr[6];
float water_arr[6];

void setup(){
	Serial.begin(9600);
	pinMode(soil_pin, INPUT);
	pinMode(water_pin, INPUT);
}
// invert :1 soil , 0 water 
float calcPercent(int sensorValue, int dryVal, int wetVal, bool invert = false){
	float val; 
	if(invert)
		val = (float)(dryVal - sensorValue) * 100.0 /(dryVal - wetVal);
	else
		val = (float)(sensorValue - dryVal) * 100.0 / (wetVal - dryVal);
	if (val > 100) val = 100;
	if (val <0 ) val = 0;
	return val;
}

void loop(){

	if (millis() - ReadTime >= time){
		ReadTime = millis();
		
		int soil_v = analogRead(soil_pin);
		int water_v = analogRead(water_pin);

		soil_arr[count] = calcPercent(soil_v, soilDry ,soilWet, true);
		water_arr[count] = calcPercent(water_v,waterDry,waterWet, false);
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
