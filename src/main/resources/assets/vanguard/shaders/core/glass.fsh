#version 330

// Liquid glass over the captured frame.
//
// The glass is a slab with a curved rim (a squircle profile, flat in the middle). A ray looking
// straight down refracts where the rim is tilted, so the rim shows the frame from further inside:
// the edge bends and magnifies what's behind it, while the middle stays undistorted. Light
// reflects off the rim as a thin highlight that is brightest where the rim faces the light.
//
// All distances are in physical pixels. local is the pixel's offset from the glass's center,
// y pointing down; the frame's texture coordinates point up.

uniform sampler2D Sampler0; // the frame
uniform sampler2D Sampler1; // the frame, blurred

in vec2 local;
in vec4 tint;          // rgb: tint color, a: how strongly to tint
flat in vec4 shape;    // half width, half height, corner radius, bezel width
flat in vec4 optics;   // thickness, frost, specular, opacity
flat in vec4 light;    // cursor x, cursor y (relative to the center), glow, shadow size

out vec4 fragColor;

const float IOR = 1.5;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

// Outward direction of the box's nearest edge.
vec2 boxNormal(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    vec2 n;
    if (q.x > 0.0 && q.y > 0.0) n = q / max(length(q), 1e-4);
    else n = q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    return n * vec2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
}

vec3 screen(vec3 base, float light) {
    return 1.0 - (1.0 - base) * (1.0 - clamp(light, 0.0, 1.0));
}

void main() {
    vec2 halfSize = shape.xy;
    float radius = shape.z;
    float bezel = max(shape.w, 1.0);
    float opacity = optics.w;

    float d = roundedBox(local, halfSize, radius);

    // Soft shadow, a little below the glass.
    float shadowSize = light.w;
    float shadowAlpha = 0.0;
    if (shadowSize > 0.5) {
        float ds = roundedBox(local - vec2(0.0, shadowSize * 0.28), halfSize, radius);
        float s = 1.0 - smoothstep(-shadowSize * 0.45, shadowSize, ds);
        shadowAlpha = s * s * 0.40 * opacity;
    }

    float coverage = clamp(0.5 - d, 0.0, 1.0);
    if (coverage <= 0.0) {
        if (shadowAlpha < 0.003) discard;
        fragColor = vec4(0.0, 0.0, 0.0, shadowAlpha);
        return;
    }

    // The rim's normal comes from a box whose corners are at least as round as the bezel is
    // wide, so it turns smoothly all the way through the bezel.
    vec2 n = boxNormal(local, halfSize, max(radius, bezel));
    float depth = max(-d, 0.0);
    float x = clamp(depth / bezel, 0.0, 1.0);
    float t = 1.0 - x;                                   // 1 at the rim, 0 past the bezel
    float t4 = t * t * t * t;
    float height = pow(max(1.0 - t4, 0.0), 0.25);        // squircle profile
    float slope = t * t * t / pow(max(1.0 - t4, 1e-4), 0.75);
    float thickness = optics.x * bezel;
    float tilt = atan(slope * optics.x);                 // surface tilt from horizontal
    float refracted = asin(sin(tilt) / IOR);             // Snell's law, air to glass
    float shift = tan(tilt - refracted) * thickness * (height + 0.3);

    // Sample towards the middle: the rim magnifies. Red bends a little less than blue.
    vec2 toward = vec2(-n.x, n.y) * shift;
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / size;
    vec2 step = toward / size;
    float spread = 0.07;
    vec3 sharp = vec3(
        texture(Sampler0, uv + step * (1.0 - spread)).r,
        texture(Sampler0, uv + step).g,
        texture(Sampler0, uv + step * (1.0 + spread)).b);
    vec3 frosted = vec3(
        texture(Sampler1, uv + step * (1.0 - spread)).r,
        texture(Sampler1, uv + step).g,
        texture(Sampler1, uv + step * (1.0 + spread)).b);

    // Frosted in the middle, clearer towards the rim where the bending shows.
    float frost = optics.y * mix(0.45, 1.0, smoothstep(0.0, 1.0, x));
    vec3 color = mix(sharp, frosted, frost);

    // A touch more saturation, like looking through real glass.
    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, 1.18);

    // Tint more over bright backgrounds so white text on the glass stays readable, and never let
    // the glass get much brighter than a mid grey.
    float behind = dot(frosted, LUMA);
    float amount = tint.a * mix(0.85, 1.4, smoothstep(0.25, 0.8, behind));
    color = mix(color, tint.rgb, clamp(amount, 0.0, 0.94));
    float lit = dot(color, LUMA);
    const float KNEE = 0.3;
    if (lit > KNEE) color *= (KNEE + (lit - KNEE) * 0.35) / lit;

    // Light comes from the top left, leaning towards the cursor when it's near.
    vec2 toLight = vec2(-0.55, -0.83);
    if (light.z > 0.0) {
        vec2 lean = -light.xy / max(length(light.xy), 1.0);
        toLight = normalize(mix(toLight, -lean, 0.35 * light.z));
    }
    float facing = dot(n, toLight);
    float highlight = pow(max(facing, 0.0), 1.4) + 0.55 * pow(max(-facing, 0.0), 1.6);
    float rim = exp(-depth / (0.9 + 0.05 * bezel));
    float sheen = t * t * (0.35 + 0.65 * max(facing, 0.0));
    float specular = optics.z;
    color = screen(color, specular * (rim * (0.16 + 0.62 * highlight) + 0.07 * sheen));
    // The top catches a little more light than the bottom.
    color = screen(color, 0.035 * specular * clamp(0.5 - local.y / (2.0 * halfSize.y), 0.0, 1.0));

    // Light glowing inside the glass under the cursor.
    if (light.z > 0.0) {
        float reach = max(min(halfSize.x, halfSize.y) * 1.3, 40.0);
        vec2 away = local - light.xy;
        color = screen(color, light.z * 0.2 * exp(-dot(away, away) / (reach * reach)));
    }

    // Glass over its own shadow.
    float alpha = coverage * opacity;
    float outAlpha = alpha + shadowAlpha * (1.0 - alpha);
    if (outAlpha < 0.003) discard;
    fragColor = vec4(color * alpha / outAlpha, outAlpha);
}
