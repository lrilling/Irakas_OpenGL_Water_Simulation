#version 450

#include semantic.glsl

// Incoming variables from
layout (location = 0) in Block
{
    vec3 Color;
    vec3 N;
    vec3 worldVertex;
    vec4 clipSpace;
    vec3 toCameraVector;
};

layout (std140, binding = LIGHT0) uniform Light0
{
    vec3 lightPos;
    vec3 lightAmbient;
    vec3 lightDiffuse;
    vec3 lightSpecular;
};

layout (std140, binding = MATERIAL) uniform Material
{
    float shininess;
};

layout (std140, binding = CAMERA) uniform Camera
{
    vec3 cameraPos;
};

// Outgoing final color
layout (location = 0) out vec4 outputColor;

// Texture samplers
uniform sampler2D reflectionTextSampler;
uniform sampler2D refractionTextSampler;
uniform sampler2D depthMap;

vec3 L;
vec3 NN;
vec3 V;
vec3 R;

vec4 textureColor;

vec4 ambient;
vec4 diffuse;
vec4 specular;

vec2 ndc;

vec2 cameraAngle;

void main()
{
    // Normalize the interpolated normal to ensure unit length
    NN = normalize(N);

    // ================ CREATING THE WATER COLOR =====================

    // Transfrom to the Normalized Device Space by using perspective division and map the coordinate system
    ndc = (clipSpace.xy/clipSpace.w)/2.0 + 0.5;

    // Calculate the texture coordinates
    vec2 reflectionTextCoords = vec2(ndc.x, -ndc.y);
    vec2 refractionTextCoords = vec2(ndc.x, ndc.y);

    // Calculate the water depth using the depth stored in the texture and the gl_FragCoord.z variable
    float near = 1.0;
    float far = 100.0;

    float textDepth = texture(depthMap, refractionTextCoords).r;
    float floorDistance = 2.0 * near * far / (far + near -(2.0 * textDepth - 1.0) * (far - near));

    float depth = gl_FragCoord.z;
    float waterDistance = 2.0 * near * far / (far + near -(2.0 * depth - 1.0) * (far - near));

    float waterDepth = floorDistance - waterDistance;

    // Calculate the distortion of the texture using the X and Z components of the normal
    vec2 distortion = vec2(NN.x * 0.5, NN.z * 0.5);

    // Get the texture colors with the calculated coordinates and the distortion
    vec4 reflectionTextColor = texture(reflectionTextSampler, reflectionTextCoords + distortion).rgba;
    vec4 refractionTextColor = texture(refractionTextSampler, refractionTextCoords + distortion).rgba;

    // Calculate the refractive factor using the view vector
    vec3 viewVector = normalize(toCameraVector);
    float refractiveFactor = dot(viewVector, NN);

    // Increase the refractive factor to get a more appealing result
    refractiveFactor = pow(refractiveFactor, 0.6);

    // Retrieve the texture color by mixing both textures
    textureColor = mix(reflectionTextColor, refractionTextColor, refractiveFactor);

    // Add some additional color to the surface of the water for more appealing result
    textureColor = mix(textureColor, vec4(0.0, 0.3, 0.5, 1.0), 0.2);

    // Smooth the edges of the water quad by adding transparency
    textureColor.a = clamp(waterDepth/3.0, 0.0, 1.0);

    // ================ ADDING THE LIGHT =====================

    // Find the unit length normal giving the direction from the vertex to the light
    L = normalize(lightPos - worldVertex);

    // Find the unit length normal giving the direction from the vertex to the camera
    V = normalize(toCameraVector);

    // Find the unit length reflection normal
    R = normalize(reflect(-L,NN));

    // Calculate the ambient component using the texture as the material color
    ambient = vec4(lightAmbient, 1) * textureColor;

    // Calculate the diffuse component using the texture as the material color
    diffuse = vec4(max(dot(L, NN), 0.0) * lightDiffuse, 1) * textureColor;

    // Calculate the specular component using the texture as the material color and the shininess uniform
    specular = vec4(pow(max(dot(V, R), 0.0), shininess) * lightSpecular, 1) * textureColor;

    // Put it all together
    outputColor = ambient + diffuse + specular;
}
