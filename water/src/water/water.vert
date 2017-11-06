
#version 450

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

void main() {
	tmp_x = 10 * position.x;
	tmp_z = 10 * position.z;

	if(tmp_x == 0 || tmp_z == 0){
		tmp = 0.2;
	}
	else{
		tmp = 0.2 * (sin(tmp_x)/tmp_x + sin(tmp_z)/tmp_z);
	}

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
