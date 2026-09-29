#version 330

// Dual-filter blur, upsampling half: a tent of eight taps around each output pixel.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 h = 0.75 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord + vec2(-h.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, h.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(h.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -h.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}
