package com.spring.listener;

import com.spring.entity.Role;
import com.spring.util.LoggerUtil;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisMessageListener2 implements MessageListener {
	private RedisTemplate redisTemplate;

	Logger logger = LoggerUtil.getLog(RedisMessageListener.class);

	@Override
	public void onMessage(Message message, byte[] bytes) {
		// 获取消息
		byte[] body = message.getBody();

		// 使用值序列化器转换
		Role role = (Role) deseriaMessage(body);
		logger.info(role.getName());

		// 获取channel
		byte[] channel = message.getChannel();

		// 使用字符串序列化器转换
		String channelStr = (String) deseriaMessage(channel);
		logger.info(channelStr);

		// 渠道名称转换
		String bytesStr = new String(bytes);
		logger.info(bytesStr);
	}

	public Object deseriaMessage(byte[] msg){
		return getRedisTemplate().getDefaultSerializer().deserialize(msg);
	}

	public RedisTemplate getRedisTemplate() {
		return redisTemplate;
	}

	public void setRedisTemplate(RedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}
}
