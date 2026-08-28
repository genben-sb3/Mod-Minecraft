package com.boyben.holobuild.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Passes vertices straight through, but forces every one of them to the same
 * transparency. That is what turns a normal block model into a ghost.
 *
 * VertexConsumer only has six methods that are not defaults, so wrapping it is
 * cheap - and every convenience method funnels down into setColor, which means
 * overriding that one is enough to catch all colour writes.
 */
public class GhostVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final int alpha;

    /** @param alpha 0-255, how solid the ghost looks */
    public GhostVertexConsumer(VertexConsumer delegate, int alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int ignoredAlpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        delegate.setNormal(normalX, normalY, normalZ);
        return this;
    }
}
