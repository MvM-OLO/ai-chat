package org.example.server.domain;

import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.Table;
import org.example.server.domain.proxy.ChatSessionProxy;

import java.time.LocalDateTime;

/**
 * 聊天会话表 实体类。
 *
 * @author easy-query-plugin automatic generation
 * @since 1.0
 */
@Data
@Table(value = "chat_session")
@EntityProxy
public class ChatSession implements ProxyEntityAvailable<ChatSession , ChatSessionProxy> {

    /**
     * 主键ID
     */
    @Column(primaryKey = true, value = "id")
    private String id;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
