
#version 450

#include classicNoise3D.glsl

#define EULER 2.7182818284590452353602874

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
float tmp_x;
float tmp_z;
float tmp_t;
float tmp;
float pi = 3.1416;

float r;

float constant_1 = 20;
float constant_2 = 0.1;

float height = 0.2;

float gauss(float x, float y){
	float result;
	result = 1/(2*pi*constant_1);
	result = result * pow(EULER, -(x*x + y*y) / (2*constant_1*constant_1));

	return result;
}



void main() {
	tmp = 0;
	for(int i = 0; i < dropCount; i++){
		tmp_t = dropData[3*i];
		tmp_x = dropData[3*i + 1];
		tmp_z = dropData[3*i + 2];

		r = 3*sqrt(pow(position.x - tmp_x,2) + pow(position.z - tmp_z ,2));
		if(tmp_t != 0){
			tmp = tmp + 4*(sin((r/constant_2)-tmp_t) * gauss(10*(r), height - ((height/(height-1))-1.5*tmp_t)));
		}
		else{
			tmp = position.y;
		}

	}

	tmp = tmp + 0.04*abs(pnoise(vec3(10*position.x-0.05*nt, position.y, 10*position.z+0.05*nt), vec3(5.0, 5.0, 5.0)));

	positionSine = vec3(position.x, tmp, position.z);

	clipSpace = normalize(proj * (view * (model * vec4(positionSine,  1))));

    // Normally gl_Position is in Clip Space and we calculate it by multiplying together all the matrices
    gl_Position = clipSpace;

    // Set the world vertex for calculating the light direction in the fragment shader
    worldVertex = vec3(model * vec4(positionSine, 1));

    // Set the transformed normal
    N = mat3(model) * normal;

    // Set the texture coordinate
    Color = color;

    //Set the vector pointing to the camera
    toCameraVector = cameraPos - worldVertex;

}
