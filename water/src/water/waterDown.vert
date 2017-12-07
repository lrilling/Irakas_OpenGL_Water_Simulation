
#version 450

#include waterFunctions.glsl
#include semantic.glsl

#define EULER 	2.7182818284590452353602874
#define PI  	3.1415926535897932384626433

// Incoming vertex position, Model Space.
layout (location = POSITION) in vec3 position;

// Incoming texture coordinate
layout (location = COLOR) in vec3 color;

// Incoming normal
layout (location = NORMAL) in vec3 normal;

// Projection and view matrices.
layout (binding = TRANSFORM0) uniform Transform0
{
    mat4 proj;
    mat4 view;
};

// model matrix
layout (binding = TRANSFORM1) uniform Transform1
{
    mat4 model;
};

layout (binding = CAMERA) uniform Camera
{
	vec3 cameraPos;
};

//noise time
layout (binding = NOISE_TIME) uniform Noise_Time{
	float nt;
};

//dropData:
layout (binding = DROP_DATA) uniform Drop_Data
{
	float dropData[300];
};

//dropCount
layout (binding = DROP_COUNT) uniform Drop_Count
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

				float refl_r = r = 3*sqrt(pow(x - refl,2) + pow(z,2))/(widthOfWater);

				tmp = tmp + 0.3 * dropFunction(refl_r, tmp_t);

				refl = -2*widthOfWater - tmp_x;

				refl_r = r = 3*sqrt(pow(x - refl,2) + pow(z ,2))/(widthOfWater);

				tmp = tmp + 0.3 * dropFunction(refl_r, tmp_t);

				refl = 2*widthOfWater - tmp_z;

				refl_r = r = 3*sqrt(pow(x,2) + pow(z - refl ,2))/(widthOfWater);

				tmp = tmp + 0.3 * dropFunction(refl_r, tmp_t);

				refl = -2*widthOfWater - tmp_z;

				refl_r = r = 3*sqrt(pow(x,2) + pow(z - refl,2))/(widthOfWater);

				tmp = tmp + 0.3 * dropFunction(refl_r, tmp_t);
			}
			else{
				tmp = position.y;
			}

		}

		tmp = tmp + noiseFunction(x, tmp, z,nt);
		return tmp;
}



void main() {
	float tmp_y = addDrops(position.x, position.z);
	positionSine = vec3(position.x, position.y + tmp_y, position.z);

	float DYdx = (addDrops(position.x - 0.005, position.z) - tmp_y) / 0.005;
	float DYdz = (addDrops(position.x, position.z - 0.005) - tmp_y) / 0.005;

	vec3 tangentX = vec3(1, DYdx, 0);
	vec3 tangentZ = vec3(0, DYdz, 1);

	vec3 computedNormal = cross(tangentX, tangentZ);
	//vec3 computedNormal = vec3(DYdx, -1, DYdz);

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
