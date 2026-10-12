package com.example.demo.controller;

import com.example.demo.api.ApiResponse;
import com.example.demo.model.dto.RuntimeInfoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * 运行时自检接口：用于确认 JDK 与虚拟线程配置在真实运行环境中生效（而不是只看配置文件）。
 *
 * <p>{@code requestOnVirtualThread} 为 {@code true} 说明 Tomcat 已用虚拟线程处理请求
 * （{@code spring.threads.virtual.enabled=true}）；{@code asyncExecutorOnVirtualThread} 说明
 * Boot 自动配置的 {@code applicationTaskExecutor} 同样运行在虚拟线程上。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final AsyncTaskExecutor applicationTaskExecutor;
    private final String applicationName;

    public SystemController(AsyncTaskExecutor applicationTaskExecutor,
                            @Value("${spring.application.name:Demo}") String applicationName) {
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.applicationName = applicationName;
    }

    @GetMapping("/runtime")
    public ApiResponse<RuntimeInfoDTO> runtime() {
        var requestThread = Thread.currentThread();
        var asyncExecutorOnVirtualThread = CompletableFuture
                .supplyAsync(() -> Thread.currentThread().isVirtual(), applicationTaskExecutor)
                .join();
        return ApiResponse.ok(new RuntimeInfoDTO(
                applicationName,
                Runtime.version().toString(),
                System.getProperty("java.vendor"),
                requestThread.isVirtual(),
                asyncExecutorOnVirtualThread,
                requestThread.toString(),
                Instant.now()));
    }
}
