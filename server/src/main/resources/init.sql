-- 1. 创建会话表
CREATE TABLE `chat_session` (
                                `id` varchar(36) NOT NULL COMMENT '主键ID',
                                `title` varchar(100) DEFAULT NULL COMMENT '会话标题',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 2. 创建消息记录表
CREATE TABLE `chat_message` (
                                `id` varchar(36) NOT NULL COMMENT '主键ID',
                                `session_id` varchar(36) NOT NULL COMMENT '关联的会话ID',
                                `role` varchar(20) NOT NULL COMMENT '角色(user/assistant/system)',
                                `content` text NOT NULL COMMENT '消息内容',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_session_id` (`session_id`) -- 加个索引，按会话查询消息时会快很多
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';