package com.anthropic.claude.sdk.transport;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;

/**
 * Claude Code 通信的低层传输接口。
 *
 * <p>这是非默认实现（例如基于 SSH 的远程 CLI、自定义守护进程等）的
 * 扩展点。默认实现是
 * {@link com.anthropic.claude.sdk.transport.subprocess.SubprocessCliTransport}。
 *
 * <p>该接口对应 Python SDK 的 {@code Transport} ABC：只处理原始 I/O。
 * 控制协议层（请求/响应关联、钩子等）位于其之上。
 */
public interface Transport extends AutoCloseable {

    /**
     * 启动传输并准备 I/O。
     */
    void connect();

    /**
     * 向传输写入原始数据（通常是 JSON + 换行符）。
     *
     * @param data  要写入的原始字符串
     */
    void write(String data);

    /**
     * 从传输读取已解析的 JSON 消息。阻塞迭代器。
     *
     * @return JSON 消息的迭代器
     */
    Iterator<JsonNode> readMessages();

    /**
     * 结束输入流（对于进程传输，关闭 stdin）。
     */
    void endInput();

    /**
     * 检查传输是否已准备好发送/接收消息。
     *
     * @return 如果已就绪则返回 {@code true}
     */
    boolean isReady();

    /**
     * 关闭传输并释放所有资源。
     */
    @Override
    void close();
}
