#version 330

// The world behind the menu: the captured frame, blurred, dimmed and vignetted.

uniform sampler2D Sampler0; // the frame
uniform sampler2D Sampler1; // the frame, blurred

in vec4 params; // r: blur, g: dim, b: vignette, a: opacity

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec3 color = mix(texture(Sampler0, uv).rgb, texture(Sampler1, uv).rgb, params.r);

    // Slightly muted, so the glass is the most vivid thing on screen.
    color = mix(vec3(dot(color, LUMA)), color, 0.86);

    vec2 centered = (uv - 0.5) * vec2(size.x / size.y, 1.0);
    float vignette = smoothstep(0.3, 1.05, length(centered));
    color *= (1.0 - params.g) * (1.0 - params.b * vignette);
    fragColor = vec4(color, params.a);
}
