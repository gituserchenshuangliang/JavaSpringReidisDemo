package com.spring.listener;

import com.spring.util.LoggerUtil;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisMessageListener implements MessageListener {
	private RedisTemplate redisTemplate;
	Logger logger = LoggerUtil.getLog(RedisMessageListener.class);
	@Override
	public void onMessage(Message message, byte[] bytes) {
		// 获取消息
		byte[] body = message.getBody();

		// 使用值序列化器转换
		String msgBody = (String) deseriaMessage(body);
		logger.info(msgBody);

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
		return getRedisTemplate().getStringSerializer().deserialize(msg);
	}

	public RedisTemplate getRedisTemplate() {
		return redisTemplate;
	}

	public void setRedisTemplate(RedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}
}
