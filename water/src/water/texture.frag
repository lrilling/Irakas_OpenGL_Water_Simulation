#version 450

// Incoming interpolated UV coordinates.
layout (location = 0) in Block
{
    vec3 Color;
    vec3 N;
    vec3 worldVertex;
    vec4 clipSpace;
    vec3 toCameraVector;
};

layout (std140, binding = 4) uniform Light0
{
    vec3 lightPos;
    vec3 lightAmbient;
    vec3 lightDiffuse;
    vec3 lightSpecular;
};

layout (std140, binding = 5) uniform Material
{
    float shininess;
};

layout (std140, binding = 6) uniform Camera
{
    vec3 cameraPos;
};

// Outgoing final color.
layout (location = 0) out vec4 outputColor;

// Texture samplers
uniform sampler2D reflectionTextSampler;
uniform sampler2D refractionTextSampler;
uniform sampler2D depthMap;

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

// Normalize device space
vec2 ndc;

void main()
{
    // Normalize the interpolated normal to ensure unit length
//    NN = normalize(cross(dFdx(worldVertex), dFdy(worldVertex)));
    NN = normalize(N);

    // We normalize the clip space by using perspective division and we convert the coordinate system
    ndc = (clipSpace.xy/clipSpace.w)/2.0 + 0.5;

    vec2 reflectionTextCoords = vec2(ndc.x, -ndc.y);
    vec2 refractionTextCoords = vec2(ndc.x, ndc.y);

    float near = 1.0;
    float far = 100.0;
    float depth = texture(depthMap, refractionTextCoords).r;
    float floorDistance = 2.0 * near * far / (far + near -(2.0 * depth - 1.0) * (far - near));

    depth = gl_FragCoord.z;
    float waterDistance = 2.0 * near * far / (far + near -(2.0 * depth - 1.0) * (far - near));
    float waterDepth = floorDistance - waterDistance;

    vec2 distortion = vec2(NN.x, NN.y);

    vec4 reflectionTextColor = texture(reflectionTextSampler, reflectionTextCoords + distortion).rgba;
    vec4 refractionTextColor = texture(refractionTextSampler, refractionTextCoords + distortion).rgba;

    //Set the vector pointing to the camera
    vec3 viewVector = normalize(toCameraVector);
    float refractiveFactor = dot(viewVector, NN);
    refractiveFactor = pow(refractiveFactor, 0.8);

    // Retrieve the texture color by mixing both textures
    textureColor = mix(reflectionTextColor, refractionTextColor, refractiveFactor);
    textureColor = mix(textureColor, vec4(0.0, 0.3, 0.5, 1.0), 0.2);
    textureColor.a = clamp(waterDepth/0.2, 0.0, 1.0);

//    textureColor = vec4(waterDepth/50.0);

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
