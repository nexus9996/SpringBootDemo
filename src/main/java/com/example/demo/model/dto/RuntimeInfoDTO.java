package com.example.demo.model.dto;

import java.time.Instant;

/**
 * 运行时诊断信息：用于确认"虚拟线程 + 现代 JDK"确实生效（请求线程与异步执行器线程都是虚拟线程）。
 */
public record RuntimeInfoDTO(
        String applicationName,
        String javaVersion,
        String javaVendor,
        boolean requestOnVirtualThread,
        boolean asyncExecutorOnVirtualThread,
        String requestThreadName,
        Instant serverTime
) {
}
