#version 450

#include semantic.glsl

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

layout (binding = CLIP_PLANE) uniform ClipPlane
{
    vec4 clip_plane;
};

// Output
layout (location = 0) out Block
{
    vec3 Color;
    vec3 N;
    vec3 worldVertex;
};

void main() {

    // Normally gl_Position is in Clip Space and we calculate it by multiplying together all the matrices
    gl_Position = normalize(proj * (view * (model * vec4(position,  1))));

    // Set the world vertex for calculating the light direction in the fragment shader
    worldVertex = normalize(vec3(model * vec4(position, 1)));

    // Set the world position without normalization for calculating the distance to the clipping plane
    vec4 worldPosition = model * vec4(position, 1);

    gl_ClipDistance[0] = dot(worldPosition, clip_plane);

    // Set the transformed normal
    N = mat3(model) * normal;

    // Set the texture coordinate
    Color = color;

}
