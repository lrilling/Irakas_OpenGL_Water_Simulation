
#version 450

#include classicNoise3D.glsl

#define EULER 	2.7182818284590452353602874
#define PI  	3.1415926535897932384626433

// Incoming vertex position, Model Space.
layout (location = 0) in vec3 position;

// Incoming texture coordinate
layout (location = 1) in vec3 color;

// Incoming normal
layout (location = 2) in vec3 normal;

// Projection and view matrices.
layout (binding = 1) uniform Transform0
{
    mat4 proj;
    mat4 view;
};

// model matrix
layout (binding = 2) uniform Transform1
{
    mat4 model;
};

//time
layout (binding = 3) uniform Time
{
	float t;
};

layout (binding = 6) uniform Camera
{
	vec3 cameraPos;
};

//noise time
layout (binding = 8) uniform Noise_Time{
	float nt;
};

//dropData:
layout (binding = 9) uniform Drop_Data
{
	float dropData[300];
};

//dropCount
layout (binding = 10) uniform Drop_Count
{
	float dropCount;
};

// Output
layout (location = 0) out Block
{
    vec3 Color;
    vec3 N;
    vec3 worldVertex;
    vec4 clipSpace;
    vec3 toCameraVector;
};

vec3 positionSine;

float r;

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
	return 4*(widthOfWater)*(sin((r/constant_2)-0.6*t) * gauss(10*((r-0.1*t)/(0.05*t)), height - ((height/(height-1))-1.5*t)));
}


float addDrops(float x, float z){
	float tmp_x;
	float tmp_z;
	float tmp_t;
	float tmp;

	tmp = 0;
		for(int i = 0; i < dropCount; i++){
			tmp_t = dropData[3*i];
			tmp_x = dropData[3*i + 1];
			tmp_z = dropData[3*i + 2];

			r = 3*sqrt(pow(x - tmp_x,2) + pow(z - tmp_z ,2))/(widthOfWater);
			if(tmp_t != 0){
				tmp = tmp + dropFunction(r, tmp_t);

				float refl = 2*widthOfWater - tmp_x;

				float refl_r = r = 3*sqrt(pow(x - refl,2) + pow(z - 0 ,2))/(widthOfWater);

				tmp = tmp + 0.8 * dropFunction(refl_r, tmp_t);

				refl = -2*widthOfWater - tmp_x;

				refl_r = r = 3*sqrt(pow(x - refl,2) + pow(z - 0 ,2))/(widthOfWater);

				tmp = tmp + 0.8 * dropFunction(refl_r, tmp_t);

				refl = 2*widthOfWater - tmp_z;

				refl_r = r = 3*sqrt(pow(x - 0,2) + pow(z - refl ,2))/(widthOfWater);

				tmp = tmp + 0.9 * dropFunction(refl_r, tmp_t);

				refl = -2*widthOfWater - tmp_z;

				refl_r = r = 3*sqrt(pow(x - 0,2) + pow(z - refl,2))/(widthOfWater);

				tmp = tmp + 0.7 * dropFunction(refl_r, tmp_t);



				//get reflections:
				/*
				float refl_x[4];
				float refl_z[4];

				refl_x[0] = 2*widthOfWater - tmp_x;
				refl_x[1] = -2*widthOfWater - tmp_x;
				refl_x[2] = 0; refl_x[3] = 0;

				refl_z[0] = 0; refl_z[1] = 0;
				refl_z[2] = 2*widthOfWater - tmp_z;
				refl_z[3] = 2*widthOfWater - tmp_z;

				float refl_r;
				for(int j = 0; j < 4; i++){
					refl_r = 3*sqrt(pow(x - refl_x[i],2) + pow(z - refl_z[i] ,2))/(widthOfWater);

					tmp = tmp + 4*(widthOfWater)*(sin((refl_r/constant_2)-0.6*tmp_t) * gauss(10*((refl_r-0.3*tmp_t)/(0.1*tmp_t)), height - ((height/(height-1))-1.5*tmp_t)));
				}
				*/
			}
			else{
				tmp = position.y;
			}

		}

		tmp = tmp + 0.04*abs(pnoise(vec3(2*x-0.05*nt, position.y, 2*z+0.05*nt), vec3(5.0, 5.0, 5.0)));
		return tmp;
}



void main() {
	float tmp_y = addDrops(position.x, position.z);
	positionSine = vec3(position.x, tmp_y, position.z);

	float dx = addDrops(position.x - 0.05, position.z) - tmp_y;
	float dz = addDrops(position.x, position.z - 0.05) - tmp_y;

	vec3 computedNormal = vec3(dx, 1, dz);

	clipSpace = normalize(proj * (view * (model * vec4(positionSine,  1))));

    // Normally gl_Position is in Clip Space and we calculate it by multiplying together all the matrices
    gl_Position = clipSpace;

    // Set the world vertex for calculating the light direction in the fragment shader
    worldVertex = vec3(model * vec4(positionSine, 1));

    // Set the transformed normal
//    N = mat3(model) * normal;
    N = mat3(model) * normalize(computedNormal);

    // Set the texture coordinate
    Color = color;

    //Set the vector pointing to the camera
    toCameraVector = cameraPos - worldVertex;

}
