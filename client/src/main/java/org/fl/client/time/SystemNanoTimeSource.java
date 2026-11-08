package org.fl.client.time;

import org.springframework.stereotype.Component;

@Component
public class SystemNanoTimeSource implements TimeSource {
    @Override
    public long nanos() {
        return System.nanoTime();
    }
}
