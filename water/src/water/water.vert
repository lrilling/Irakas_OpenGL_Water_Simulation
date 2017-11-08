
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

//noise time
layout (binding = 4) uniform Noise_Time{
	float nt;
};

// Output
layout (location = 0) out Block
{
    vec3 Color;
    vec3 N;
    vec3 worldVertex;
};

vec3 positionSine;
float tmp_x;
float tmp_z;
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
	tmp_x = position.x;
	tmp_z = position.y;
//
//	if(tmp_x == 0 || tmp_z == 0){
//		tmp = 0.2;
//	}
//	else if(tmp_x == 0 || tmp_z == 0){
//		tmp = 0.4;
//	}
//	else{
//		tmp = 0.2 * (sin(tmp_x)/tmp_x + cos(tmp_z)/tmp_z);
//	}

	r = sqrt(pow(position.x,2) + pow(position.z,2));
	if(t != 0){
		tmp = 4*(sin((r/constant_2)-t) * gauss(10*r, height - ((height/(height-1))-t)));
	}
	else{
		tmp = position.y;
	}
	//tmp = height * sin((r / constant_2)+t);

	tmp = tmp + 0.1*abs(pnoise(vec3(position.x-0.05*nt, position.y, position.z+0.05*nt), vec3(10.0, 10.0, 10.0)));

	positionSine = vec3(position.x, tmp, position.z);

    // Normally gl_Position is in Clip Space and we calculate it by multiplying together all the matrices
    gl_Position = proj * (view * (model * vec4(positionSine,  1)));

    // Set the world vertex for calculating the light direction in the fragment shader
    worldVertex = vec3(model * vec4(position, 1));

    // Set the transformed normal
    N = mat3(model) * normal;

    // Set the texture coordinate
    Color = color;

}
