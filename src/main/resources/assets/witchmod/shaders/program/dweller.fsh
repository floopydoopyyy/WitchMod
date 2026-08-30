#version 150

uniform sampler2D DiffuseSampler;
uniform float DreadAmount;   // 0 = untouched, 1 = fully drained + darkened

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    // Rec. 601 luma — the fully-drained greyscale target.
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    g = clamp((g - 0.5) * 1.12 + 0.5, 0.0, 1.0) * 0.9;
    vec3 grey = vec3(g);
    // Roll the colour out gradually as dread rises.
    float a = clamp(DreadAmount, 0.0, 1.0);
    vec3 outc = mix(c.rgb, grey, a);
    fragColor = vec4(outc, 1.0);
}
