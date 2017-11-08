#version 450

// Incoming interpolated UV coordinates.
layout (location = 0) in Block
{
    vec2 UV;
    vec3 N;
    vec3 worldVertex;
};

layout (std140, binding = 3) uniform Light0
{
    vec3 lightPos;
    vec3 lightAmbient;
    vec3 lightDiffuse;
    vec3 lightSpecular;
};

layout (std140, binding = 4) uniform Material
{
    float shininess;
};

layout (std140, binding = 5) uniform Camera
{
    vec3 cameraPos;
};

// Outgoing final color.
layout (location = 0) out vec4 outputColor;

// Texture sampler
uniform sampler2D textureSampler;

// Normals
vec3 L;
vec3 NN;
vec3 V;
vec3 R;

// Colors
vec4 textureColor;

vec4 ambient;
vec4 diffuse;
vec4 specular;

void main()
{
    // Retrieve the texture color
    textureColor = texture(textureSampler, UV).rgba;

    // Normalize the interpolated normal to ensure unit length
    NN = normalize(N);

    // Find the unit length normal giving the direction from the vertex to the light
    L = normalize(lightPos - worldVertex);

    // Find the unit length normal giving the direction from the vertex to the camera
    V = normalize(cameraPos - worldVertex);

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
