#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec4 Shape;
in vec4 Optics;
in vec4 Light;

out vec2 local;
out vec4 tint;
flat out vec4 shape;
flat out vec4 optics;
flat out vec4 light;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    tint = Color;
    shape = Shape;
    optics = Optics;
    light = Light;
}
