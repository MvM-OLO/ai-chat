package org.example.server.domain;

import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.Table;
import org.example.server.domain.proxy.ChatMessageProxy;

import java.time.LocalDateTime;

/**
 * 聊天消息表 实体类。
 *
 * @author easy-query-plugin automatic generation
 * @since 1.0
 */
@Data
@Table(value = "chat_message")
@EntityProxy
public class ChatMessage implements ProxyEntityAvailable<ChatMessage , ChatMessageProxy> {

    /**
     * 主键ID
     */
    @Column(primaryKey = true, value = "id")
    private String id;

    /**
     * 关联的会话ID
     */
    private String sessionId;

    /**
     * 角色(user/assistant/system)
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 发送时间
     */
    private LocalDateTime createTime;


}
