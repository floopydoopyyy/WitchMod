#version 150

// the warden's sight: a world of black and deep blue, felt more than seen. bright things (the sounds it hears, the
// light up close) burn ice-blue; red stays red (the marked). the edges close in, catch a faint glint of blue on each
// heartbeat, and the whole view ripples like it's under deep water.

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Amount;   // 0 = untouched, 1 = full warden sight
uniform float Pulse;    // the heartbeat: 1 on the beat, falling away
uniform float Clock;    // seconds, for the drift
uniform float Rage;     // 0 calm .. 1 angry: a hotter, tighter view

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float a = clamp(Amount, 0.0, 1.0);
    vec2 centre = vec2(0.5);
    vec2 d = texCoord - centre;
    float dist = length(d * vec2(InSize.x / InSize.y, 1.0)) / 0.9;

    // a slow underwater ripple, stronger towards the edges
    float edge = smoothstep(0.15, 0.95, dist);
    vec2 wave = vec2(sin(texCoord.y * 26.0 + Clock * 1.7), cos(texCoord.x * 21.0 + Clock * 1.3)) * 0.0016 * (0.35 + edge);
    vec2 uv = texCoord + wave * a;

    vec4 c = texture(DiffuseSampler, uv);
    vec3 src = c.rgb;

    float l = luma(src);
    // black to midnight to cold blue to ice
    vec3 ramp = mix(vec3(0.0, 0.004, 0.012), vec3(0.015, 0.05, 0.16), smoothstep(0.0, 0.18, l));
    ramp = mix(ramp, vec3(0.12, 0.38, 0.85), smoothstep(0.14, 0.55, l));
    ramp = mix(ramp, vec3(0.78, 0.94, 1.0), smoothstep(0.5, 0.95, l));

    // red survives: the marked
    float red = smoothstep(0.12, 0.42, src.r - max(src.g, src.b));
    vec3 blood = vec3(1.0, 0.1, 0.07) * (0.45 + 0.9 * max(src.r, l));
    vec3 seen = mix(ramp, blood, red);

    // the edges close in (tighter when it's angry), and catch a faint glint on each heartbeat
    float reach = 0.92 - 0.12 * Rage;
    float vignette = smoothstep(reach, reach - 0.62, dist);
    seen *= mix(0.08, 1.0, vignette);
    // (just the very rim: the outermost sliver of the screen, the corners most)
    float rim = smoothstep(1.0, 1.25, dist);
    seen += vec3(0.012, 0.035, 0.08) * rim * Pulse * (0.6 + 0.4 * Rage);

    // a little grain in the dark
    seen += (hash(uv * InSize + vec2(Clock * 37.0, Clock * 11.0)) - 0.5) * vec3(0.01, 0.018, 0.035);

    fragColor = vec4(mix(c.rgb, seen, a), 1.0);
}
