package com.example.detection.dto;

import java.util.concurrent.atomic.AtomicInteger;

public record RateLimitBucket(AtomicInteger count, long windowEnd) {
    public int getCount() {
        return count.get();
    }
}
