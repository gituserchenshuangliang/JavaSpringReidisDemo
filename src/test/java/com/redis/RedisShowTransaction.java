package com.redis;

import com.spring.entity.Role;
import com.spring.util.LoggerUtil;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisListCommands;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.redis.core.ZSetOperations.TypedTuple;

/**
 * @description:Redis的事务
 * @author: Cherry
 * @time: 2020/6/16 12:00:00
 * Redis 启事务是multi命令，而执行事务是exec命令。multi和exec命令之间的
 * Redis令将采取进入队列的形式，直至exec命令的出现，才会一次性发送队列里的命令
 * 去执行，而在执行这些命令的时候其他客户端就不能再插入任何命令了，这就是Redis事务机制。
 */
public class RedisShowTransaction {
    ApplicationContext app = null;
    Jedis jedis = null;
    RedisTemplate redisTemplate = null;
    Logger logger = null;

    @Before
    public void beforeTs(){
        //Context上下文
        app = new ClassPathXmlApplicationContext("config/spring-config.xml");

        //Jedis配置
        JedisPoolConfig poolCfg = new JedisPoolConfig();
        poolCfg.setMaxIdle(50);
        poolCfg.setMaxTotal(100);
        poolCfg.setMaxWaitMillis(20000);
        JedisPool pool = new JedisPool(poolCfg, "localhost");
        jedis = pool.getResource();

        //Jedis操作类
        redisTemplate = app.getBean(RedisTemplate.class);
    
        //日志
        logger = LoggerUtil.getLog(RedisShowTransaction.class);
    }

    @Test
    public void showTransaction() {
        SessionCallback<String> callBack = new SessionCallback<String>() {
            @Override
            public String execute(RedisOperations ops) throws DataAccessException {
                //开启Redis事务
                ops.multi();

                ops.boundValueOps( "key").set("一个测试值");

                // 注意由于命令只是进入队列，而没有被执行，所以此处采用get命令，而value却返回为null
                String value = (String) ops.boundValueOps("key").get();
                logger.info("事务执行过程中，命令入队列，而没有被执行，所以value为空：value=" + value);

                // 此时list会保存之前进入队列的所有命令的结果
                List list = ops.exec();// 执行事务
                logger.info("此时list会保存之前进入队列的所有命令的结果");
                logger.info(list);

                // 事务结束后，获取value1
                value = (String) redisTemplate.opsForValue().get("key");
                return value;
            }
        };

        // 执行Redis的命令
        String value = (String) redisTemplate.execute(callBack);
        logger.info(value);
    }

    /**
     * multi ... exec 事务命令是有统开销的，因为它会检测对应的锁和序列化命令。
     * 有时候我们希望在没有任何附加条件的场景下去使用队列批量执行系列的命令，
     * 从而提高系统性能，这就是Redis的流水pipelined技术。
     * */
    @Test
    public void showPipeline(){
        long start = System.currentTimeMillis();
        // 开启流水线
        Pipeline pipeline = jedis.pipelined();

        // 这里测试10万条的读写2个操作
        for (int i = 0; i < 100000; i++) {
            int j = i + 1;
            pipeline.set("pipeline_key_" + j, "pipeline_value_" + j);
            pipeline.get("pipeline_key_" + j);
        }

        //将返回执行过的命令返回的List列表结果
        List result = pipeline.syncAndReturnAll();
        logger.info("执行:"+result.size()+"次指令！");

        long end = System.currentTimeMillis();

        // 计算耗时
        logger.info("使用Pipeline耗时：" + (end - start) + "毫秒");

        /*
         * 不使用Pipline技术
         */
        start = System.currentTimeMillis();
        // 这里测试10万条的读写2个操作
        for (int i = 0; i < 100000; i++) {
            int j = i + 1;
            jedis.set("key_" + j, "pipeline_value_" + j);
            jedis.get("key_" + j);
        }
        end = System.currentTimeMillis();
        logger.info("不使用Pipeline耗时：" + (end - start) + "毫秒");

        /*
         * 使用Pipline技术和RedisTemplate
         */
        SessionCallback session = new SessionCallback<Void>() {
            @Override
            public Void execute(RedisOperations ops) throws DataAccessException {
                long start2 = System.currentTimeMillis();
                // 这里测试10万条的读写2个操作
                for (int i = 0; i < 100000; i++) {
                    int j = i + 1;
                    ops.boundValueOps("key_" + j).set("pipeline_value_" + j);
                    ops.boundValueOps("key_" + j).get();
                }
                long end2 = System.currentTimeMillis();
                logger.info("使用Pipeline和RedisTemplate耗时：" + (end2 - start2) + "毫秒");
                return null;
            }
        };
        result = redisTemplate.executePipelined(session);
        logger.info(result.size());
    }

    /**
     * Redis的发布和订阅
     * subscribe和publish指令
     * MessageListener实现类接受发布的消息
     */
    @Test
    public void showPubSub(){
        String channel = "chat";
        redisTemplate.convertAndSend(channel, "I am crazy!!");

        String channel2 = "channel";
        Role role = new Role();
        role.setId(1);
        role.setName("熔岩巨兽");
        role.setNote("坦克类型");

        //JavaBean对象序列化为Redis值
        redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());
        redisTemplate.convertAndSend(channel2,role);
    }

    /**
     * 设置Redis键生存时间
     */
    @Test
    public void showExpire(){
        SessionCallback session = new SessionCallback() {
            @Override
            public Object execute(RedisOperations ops) throws DataAccessException {
            ops.boundValueOps("key1").set("value1");
			String keyValue = (String) ops.boundValueOps("key1").get();
			Long expSecond = ops.getExpire("key1");
            logger.info(expSecond);

			boolean b = false;
			b = ops.expire("key1", 120L, TimeUnit.SECONDS);
			b = ops.persist("key1");

            logger.info(ops.getExpire("key1"));

			Long now = System.currentTimeMillis();
			Date date = new Date();
			date.setTime(now + 120000);
			ops.expireAt("key", date);
			logger.info(ops.getExpire("key1"));
			return null;
            }
        };
        redisTemplate.execute(session);
    }

    /**
     * Lua脚本与Redis
     */
    @Test
    public void showLuaScript() {
        // 如果是简单的操作，使用原来的Jedis会简易些
        Jedis jedis = (Jedis) redisTemplate.getConnectionFactory().getConnection().getNativeConnection();
        
        // 执行简单的脚本
        String helloJava = (String) jedis.eval("return 'hello java'");
        logger.info(helloJava);
        
        // 执行带参数的脚本
        jedis.eval("redis.call('mset',KEYS[1], ARGV[1])", 1, "lua-key", "lua-value");
        String luaKey = (String) jedis.get("lua-key");
        logger.info(luaKey);
        
        // 缓存脚本，返回sha1签名标识
        String sha1 = jedis.scriptLoad("redis.call('set',KEYS[1], ARGV[1])");
        
        // 通过标识执行脚本
        jedis.evalsha(sha1, 1, new String[] { "sha-key", "sha-val" });

        // 获取执行脚本后的数据
        String shaVal = jedis.get("sha-key");
        logger.info(shaVal);

        // 关闭连接
        jedis.close();
    }

    @Test
    public void showRedisScript() {
		// 定义默认脚本封装类
		DefaultRedisScript<Role> redisScript = new DefaultRedisScript<Role>();
		
		// 设置脚本
		redisScript.setScriptText("redis.call('set', KEYS[1], ARGV[1])  return redis.call('get', KEYS[1])");
		
		// 定义操作的key列表
		List<String> keyList = new ArrayList<String>();
		keyList.add("role1");
		
		// 需要序列化保存和读取的对象
		Role role = new Role();
		role.setId(1);
		role.setName("role_name_1");
		role.setNote("note_1");
		
		// 获得标识字符串
		String sha1 = redisScript.getSha1();
		logger.info(sha1);
		
		// 设置返回结果类型，如果没有这句话，结果返回为空
		redisScript.setResultType(Role.class);
		
		// 定义序列化器
		JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();

		/*
		 执行脚本
		 第一个是RedisScript接口对象，
         第二个是参数序列化器
		 第三个是结果序列化器，
         第四个是Reids的key列表，
         最后是参数列表
         */
		Role obj = (Role) redisTemplate.execute(redisScript,
                serializer, serializer, keyList, role);
		
		// 打印结果
		logger.info(obj);
    }

    @Test
    public void showLuaFile() throws IOException {
        // 读入文件流
        Path path = Paths.get("E:\\IDEAwork\\Two\\JavaSpringReidisDemo\\src\\test\\resources\\test.lua");
        byte[] bytes = getFileToByte(path);

        Jedis jedis = (Jedis) redisTemplate.getConnectionFactory().getConnection().getNativeConnection();

        // 发送文件二进制给Redis，这样redis就会返回sha1标识
        byte[] sha1 = jedis.scriptLoad(bytes);

        // 使用返回的标识执行，其中第二个参数2，表示使用2个键
        // 而后面的字符串都转化为了二进制字节进行传输
        Object obj = jedis.evalsha(sha1, 2, "key1".getBytes(), "key2".getBytes(), "2".getBytes(), "2".getBytes());
        logger.info(obj);
    }

    public byte[] getFileToByte(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
