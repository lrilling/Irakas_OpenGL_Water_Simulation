
#version 450

precision highp float;
precision highp int;


// Incoming interpolated (between vertices) color.
layout (location = 0) in Block
{
    vec3 Color;
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

//Normals
vec3 L;
vec3 NN;
vec3 V;
vec3 R;

//Colors:
vec4 textureColor;
vec4 ambient;
vec4 diffuse;
vec4 specular;


void main()
{
	textureColor = vec4(Color, 1);

	NN = normalize(N);

	L = normalize(lightPos - worldVertex);

	V = normalize(cameraPos - worldVertex);

	R = normalize(reflect(-L, NN));

	ambient = vec4(lightAmbient, 1) * textureColor;

	diffuse = vec4(max(dot(L,NN), 0.0) * lightDiffuse, 1) * textureColor;

	specular = vec4(pow(max(dot(V,R), 0.0), shininess) * lightSpecular, 1) * textureColor;

    outputColor = ambient + diffuse + specular;
}
