#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    // Push saturation up (Bedrock's punchier colours) + a touch more contrast.
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 sat = mix(vec3(g), c.rgb, 1.9);              // 1.0 = unchanged, >1 = more saturated
    sat = clamp((sat - 0.5) * 1.12 + 0.5, 0.0, 1.0);  // gentle contrast bump
    fragColor = vec4(sat, 1.0);
}
