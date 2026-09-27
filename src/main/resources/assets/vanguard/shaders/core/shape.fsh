#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 shapeCoord;
in vec4 vertexColor;

out vec4 fragColor;

// shapeCoord packs a mode and a parameter into the integer part of each component
// (see ShapeBuilder): x = 2 * mode + 0.5 + u, y = 2 * param + 0.5 + v, with u, v in [0, 1].
// (u, v) is the position inside a unit quarter circle, so length(uv) = 1 is the shape's edge.
//   mode 0: solid, anti-aliased edge. param > 0 cuts a hole of radius param / 255 (rings).
//   mode 1: soft falloff from radius param / 255 to 1 (shadows and glows).
void main() {
    vec2 cell = floor(shapeCoord * 0.5);
    vec2 uv = shapeCoord - cell * 2.0 - 0.5;
    float param = cell.y / 255.0;
    float d = length(uv);

    float coverage;
    if (cell.x < 0.5) {
        float aa = max(fwidth(d), 1e-5);
        coverage = clamp((1.0 - d) / aa + 0.5, 0.0, 1.0);
        if (cell.y > 0.5) {
            coverage *= clamp((d - param) / aa + 0.5, 0.0, 1.0);
        }
    } else {
        float t = clamp((d - param) / max(1.0 - param, 1e-5), 0.0, 1.0);
        coverage = 1.0 - t * t * (3.0 - 2.0 * t);
        coverage *= coverage;
    }

    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
