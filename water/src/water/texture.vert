
#version 450

// Incoming vertex position, Model Space.
layout (location = 0) in vec3 position;

// Incoming texture coordinate
layout (location = 3) in vec2 uv;

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
    vec2 UV;
    vec3 N; // The Normal Vertex
    vec3 worldVertex;
    vec4 clipSpace;
};

void main() {

    // Set the Clip Space by multiplying together all the matrices
	clipSpace = normalize(proj * (view * (model * vec4(position,  1))));

    // Normally gl_Position is in Clip Space
    gl_Position = clipSpace;

    // Set the world vertex for calculating the light direction in the fragment shader
    worldVertex = normalize(vec3(model * vec4(position, 1)));

    // Set the transformed normal
    N = mat3(model) * normal;

    // Set the texture coordinate
    UV = uv;



}
