package dev.vanguard.gui.anim;

public enum Easing {
    LINEAR {
        @Override
        public float apply(float t) {
            return t;
        }
    },
    CUBIC_OUT {
        @Override
        public float apply(float t) {
            float inv = 1f - t;
            return 1f - inv * inv * inv;
        }
    },
    QUINT_OUT {
        @Override
        public float apply(float t) {
            float inv = 1f - t;
            return 1f - inv * inv * inv * inv * inv;
        }
    },
    CUBIC_IN_OUT {
        @Override
        public float apply(float t) {
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    },
    /** Overshoots slightly before settling, for "springy" toggles. */
    BACK_OUT {
        @Override
        public float apply(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1f;
            float x = t - 1f;
            return 1f + c3 * x * x * x + c1 * x * x;
        }
    };

    public abstract float apply(float t);
}
