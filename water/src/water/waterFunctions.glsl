#include classicNoise3D.glsl

#define EULER 	2.7182818284590452353602874
#define PI  	3.1415926535897932384626433

float widthOfWater = 7;

float constant_1 = 20;
float constant_2 = 0.1;

float height = 0.02;

float gauss(float x, float y){
	float result;
	result = 1/(2*PI*constant_1);
	result = result * pow(EULER, -(x*x + y*y) / (2*constant_1*constant_1));

	return result;
}

float dropFunction(float r, float t){
	t = 1.5* t;
	return 4*(widthOfWater)*(sin((r/constant_2)-7*t) * gauss(10*((r-t)/(0.1*t)), height - ((height/(height-1))-10*t)));
}

float noiseFunction(float x, float y, float z, float t){
	return 0.04*pnoise(vec3(1.5*x-0.05*t, y, 1.5*z + 0.05*t), vec3(5.0, 5.0, 5.0));
	//return 0;
}
