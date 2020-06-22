package com.spring.redis;

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
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.springframework.data.redis.core.ZSetOperations.*;

/**
 * @description:Redis的测试
 * @author: Cherry
 * @time: 2020/6/14 9:44
 */
public class RedisShow {
    ApplicationContext app = null;
    Jedis jedis = null;
    RedisTemplate redisTemplate = null;
    Logger logger = null;
    HashOperations hops = null;
    ZSetOperations zops = null;
    SetOperations sops = null;
    ListOperations lops = null;
    ValueOperations vops = null;
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
    
        //Redis的String操作对象
        vops = redisTemplate.opsForValue();

        //Redis的List集合操作对象
        lops = redisTemplate.opsForList();
        
        //Redis的Set集合操作对象
        sops = redisTemplate.opsForSet();
        
        //Redis的ZSet集合操作对象
        zops = redisTemplate.opsForZSet();
        
        //Redis的Hash数据操作对象
        hops = redisTemplate.opsForHash();


        //日志
        logger = LoggerUtil.getLog(RedisShow.class);
    }

    /**
     * Redis的速度测试
     */
    @Test
    public void show1(){
        int i = 0;
        try {
            long start = System.currentTimeMillis();
            while (true) {
                long end = System.currentTimeMillis();
                if (end - start >= 1000) {
                    break;
                }
                i++;
                jedis.set("test" + i, i + "");
            }
        } finally {
            jedis.close();
        }
        logger.info("一秒钟操作"+i+"次");
    }

    /**
     * Redis存储Java对象
     * set和get可能来自Redis连接池的不同Redis链接
     * 值序列器设置为 JdkSerializationRedisSerializer
     */
    @Test
    public void showBean(){
        Role role = new Role();
        role.setId(9);
        role.setName("法师");
        role.setNote("魔法攻击");

        redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());

        redisTemplate.opsForValue().set("r1", role);
        Role role1 = (Role) redisTemplate.opsForValue().get("r1");

        logger.info(role1);
    }

    /**
     * SessionCallback可以保证set和get来自同一个Redis链接
     * 值序列器设置为 JdkSerializationRedisSerializer
     */
    @Test
    public void showBeans(){
        Role role = new Role();
        role.setId(1);
        role.setName("刺客");
        role.setNote("刺杀");

        SessionCallback<Role> callBack = new SessionCallback<Role>() {
            @Override
            public Role execute(RedisOperations ops) throws DataAccessException {
                ops.boundValueOps("role_1").set(role);
                return (Role) ops.boundValueOps("role_1").get();
            }
        };

        redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());

        Role savedRole = (Role) redisTemplate.execute(callBack);

        logger.info(savedRole);
    }

    /**
     * Redis操作String(set,get)
     */
    @Test
    public void showGet(){
        vops.set("A", "张天师");
        vops.set("B", "自以为是");
      
        String value1 = (String) vops.get("A");
        logger.info(value1);
       
        redisTemplate.delete("A");
       
        Long length = vops.size("B");
        logger.info(length);
        
        String oldValue2 = (String) vops.getAndSet("B", "见微知著");
        logger.info(oldValue2);
       
        String value2 = (String) vops.get("B");
        logger.info(value2);
        
        String rangeValue2 = vops.get("B", 0, 3);
        logger.info(rangeValue2);
        
        int newLen = vops.append("B", "_app");
        logger.info(newLen);
        String appendValue2 = (String) vops.get("B");
        logger.info(appendValue2);
    }

    /**
     * Redis操作数据加减运算(incr,incrby,decr,decrby)
     * 值序列器设置为 StringRedisSerializer
     */
    @Test
    public void showHandle(){
        vops.set("i","9");
        showGet("i");

        vops.increment("i", 1);
        showGet("i");

        redisTemplate.getConnectionFactory().getConnection().decr(redisTemplate.getKeySerializer().serialize("i"));
        showGet("i");

        redisTemplate.getConnectionFactory().getConnection().decrBy(redisTemplate.getKeySerializer().serialize("i"), 6);
        showGet("i");

        vops.increment("i", 2.3);
        showGet("i");
    }

    /**
     * Redis操作Hash
     * 值序列器设置为 StringRedisSerializer
     */
    @Test
    public void showHash(){
        String key = "hash";
        HashMap<String,String> map = new HashMap<String,String>(10);
        map.put("f1","val1");
        map.put("f2","val2");

        // 相当于hmset命令
        hops.putAll(key,map);

        // 相当于hset命令
        hops.put(key,"f3","6");
        showHash(key,"f3");

        // 相当于 hexists key filed命令
        boolean exists = hops.hasKey(key,"f3");
        logger.info(exists);

        // 相当于hgetall命令
        Map keyValMap = hops.entries(key);
        keyValMap.forEach((k,v) -> {
        logger.info(k+":"+v);
        });

        // 相当于hincrby命令
        hops.increment(key, "f3", 2);
        showHash(key,"f3");

        // 相当于hincrbyfloat命令
        hops.increment(key, "f3", 0.88);
        showHash(key, "f3");

        // 相当于hvals命令
        List valueList = hops.values(key);
        valueList.forEach((v) -> {
        logger.info(v);
        });

        // 相当于hkeys命令
        Set keyList = hops.keys(key);
        keyList.forEach((v) -> {
            logger.info(v);
        });

        // 相当于hmget命令
        List<String> fieldList = new ArrayList<String>();
        fieldList.add("f1");
        fieldList.add("f2");
        List valueList2 = hops.multiGet(key, keyList);
        valueList2.forEach((v) -> {
            logger.info(v);
        });

        // 相当于hsetnx命令
        boolean success = hops.putIfAbsent(key, "f4", "val4");
        logger.info(success);

        // 相当于hdel命令
        Long result = hops.delete(key,"f1", "f2");
        logger.info(result);
    }

    /**
     * Redis的List操作
     */
    @Test
    public void showList(){
        try {
            // 删除链表，以便我们可以反复测试
            redisTemplate.delete("list");

            // 把node3插入链表list
            lops.leftPush("list", "node3");

            // 相当于lpush把多个价值从左插入链表
            List<String> nodeList = Arrays.asList(new String[]{"绝处逢生","九死一生"});
            lops.leftPushAll("list", nodeList);

            // 从右边插入一个节点
            lops.rightPush("list", "涅槃重生");

            // 获取下标为0的节点
            String node1 = (String) lops.index("list", 0);

            // 获取链表长度
            long size = lops.size("list");

            // 从左边弹出一个节点
            String lpop = (String) lops.leftPop("list");

            // 从右边弹出一个节点
            String rpop = (String) lops.rightPop("list");

            // 注意，需要使用更为底层的命令才能操作linsert命令
            // 使用linsert命令在node2前插入一个节点
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .lInsert(
                    "list".getBytes("utf-8"),
                    RedisListCommands.Position.BEFORE,
                    "node2".getBytes("utf-8"),
                    "before_node".getBytes("utf-8"));

            // 使用linsert命令在node2后插入一个节点
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .lInsert(
                    "list".getBytes("utf-8"),
                    RedisListCommands.Position.AFTER,
                    "node2".getBytes("utf-8"),
                    "after_node".getBytes("utf-8"));

            // 判断list是否存在，如果存在则从左边插入head节点
            lops.leftPushIfPresent("list", "head");

            // 判断list是否存在，如果存在则从右边插入end节点
            lops.rightPushIfPresent("list", "end");

            // 从左到右，或者下标从0到10的节点元素
            List valueList = lops.range("list", 0, 10);

            nodeList = new ArrayList<String>();

            for (int i = 1; i <= 3; i++) {
                nodeList.add("node");
            }
            // 在链表左边插入三个值为node的节点
            lops.leftPushAll("list", nodeList);

            // 从左到右删除至多三个node节点
            lops.remove("list", 3, "node");

            // 给链表下标为0的节点设置新值
            lops.set("list", 0, "new_head_value");
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        // 链表长度
        Long size = lops.size("list");
        // 获取整个链表的值
        List valueList = lops.range("list", 0, size);
        // 打印
        logger.info(valueList);
    }

    /**
     * Redis的Set无序集合
     */
    @Test
    public void showSet(){
        Set set = null;
        // 将元素加入列表
        redisTemplate.boundSetOps("set1").add("v1", "v2", "v3", "v4", "v5", "v6");
        redisTemplate.boundSetOps("set2").add("v0", "v2", "v4", "v6", "v8");

        // 求集合长度
        long l = sops.size("set1");
        logger.info("set1长度"+l);

        // 求差集
        set = sops.difference("set1", "set2");
        logger.info("set1-set2差集");
        logger.info(set);

        // 求并集
        set = sops.intersect("set1", "set2");
        logger.info("set1-set2并集");
        logger.info(set);

        // 判断是否集合中的元素
        boolean exists = sops.isMember("set1", "v1");

        // 获取集合所有元素
        set = sops.members("set1");
        logger.info("set1所有元素");
        logger.info(set);

        // 从集合中随机弹出一个元素
        String val = (String) sops.pop("set1");

        // 随机获取一个集合的元素
        val = (String) sops.randomMember("set1");

        // 随机获取2个集合的元素
        List list = sops.randomMembers("set1", 2L);

        // 删除一个集合的元素，参数可以是多个
        sops.remove("set1", "v1");

        // 求两个集合的并集
        sops.union("set1", "set2");

        // 求两个集合的差集，并保存到集合diff_set中
        sops.differenceAndStore("set1", "set2", "diff_set");
        set = sops.members("diff_set");
        logger.info("diff_set所有元素");
        logger.info(set);

        // 求两个集合的交集，并保存到集合inter_set中
        sops.intersectAndStore("set1", "set2", "inter_set");
        set = sops.members("inter_set");
        logger.info("inter_set所有元素");
        logger.info(set);

        // 求两个集合的并集，并保存到集合union_set中
        sops.unionAndStore("set1", "set2", "union_set");
        set = sops.members("union_set");
        logger.info("union_set所有元素");
        logger.info(set);
    }

    /**
     * Redis的ZSet有序集合
     */
    @Test
    public void showZSet(){
        // Spring提供接口TypedTuple操作有序集合
        Set<TypedTuple> set1 = new HashSet<TypedTuple>();
        Set<TypedTuple> set2 = new HashSet<TypedTuple>();

        int j = 9;
        for (int i = 1; i <= 9; i++) {
            j--;
            // 计算分数和值
            Double score1 = Double.valueOf(i);
            String value1 = "x" + i;
            Double score2 = Double.valueOf(j);
            String value2 = j % 2 == 1 ? "y" + j : "x" + j;

            // 使用Spring提供的默认TypedTuple——DefaultTypedTuple
            TypedTuple typedTuple1 = new DefaultTypedTuple(value1, score1);
            set1.add(typedTuple1);

            TypedTuple typedTuple2 = new DefaultTypedTuple(value2, score2);
            set2.add(typedTuple2);
        }
        // 将元素插入有序集合zset1
        zops.add("zset1", set1);
        zops.add("zset2", set2);

        // 统计总数
        Long size = zops.zCard("zset1");

        // 计分数为score，那么下面的方法就是求3<=score<=6的元素
        size = zops.count("zset1", 3, 6);
        logger.info("计分数为score，那么下面的方法就是求3<=score<=6的元素");
        logger.info(size);

        Set set = null;
        // 从下标一开始截取5个元素，但是不返回分数,每一个元素是String
        set = zops.range("zset1", 1, 5);
        logger.info("从下标一开始截取5个元素，但是不返回分数,每一个元素是String");
        logger.info(set);

        // 截取集合所有元素，并且对集合按分数排序，并返回分数,每一个元素是TypedTuple
        set = zops.rangeWithScores("zset1", 0, -1);
        logger.info("截取集合所有元素，并且对集合按分数排序，并返回分数,每一个元素是TypedTuple");
        logger.info(set);

        // 将zset1和zset2两个集合的交集放入集合inter_zset
        size = zops.intersectAndStore("zset1", "zset2", "inter_zset");

        // 区间
        RedisZSetCommands.Range range = RedisZSetCommands.Range.range();
        range.lt("x8");// 小于
        range.gt("x1");// 大于
        set = zops.rangeByLex("zset1", range);
        logger.info("区间");
        logger.info(set);

        range.lte("x8");// 小于等于
        range.gte("x1");// 大于等于
        set = zops.rangeByLex("zset1", range);
        logger.info("区间");
        logger.info(set);

        // 限制返回个数
        RedisZSetCommands.Limit limit = RedisZSetCommands.Limit.limit();
        // 限制返回个数
        limit.count(4);
        // 限制从第五个开始截取
        limit.offset(5);
        // 求区间内的元素，并限制返回4条
        set = zops.rangeByLex("zset1", range, limit);
        logger.info("限制返回个数");
        logger.info(set);

        // 求排行，排名第1返回0，第2返回1
        Long rank = zops.rank("zset1", "x4");
        logger.info("求排行，排名第1返回0，第2返回1");
        logger.info("rank = " + rank);

        // 删除元素，返回删除个数
        size = zops.remove("zset1", "x5", "x6");
        logger.info("删除元素，返回删除个数");
        logger.info("delete = " + size);

        // 按照排行删除从0开始算起，这里将删除第排名第2和第3的元素
        size = zops.removeRange("zset2", 1, 2);

        // 获取所有集合的元素和分数，以-1代表全部元素
        set = zops.rangeWithScores("zset2", 0, -1);
        logger.info("获取所有集合的元素和分数，以-1代表全部元素");
        logger.info(set);

        // 删除指定的元素
        size = zops.remove("zset2", "y5", "y3");
        logger.info(size);

        // 给集合中的一个元素的分数加上11
        Double dbl = zops.incrementScore("zset1", "x1", 11);
        zops.removeRangeByScore("zset1", 1, 2);

        set = zops.reverseRangeWithScores("zset2", 1, 10);
        logger.info(set);
    }
    
    private void showGet(String key){
        logger.info(vops.get(key));
    }
    private void showHash(String key,String field){
        logger.info(hops.get(key,field));
    }
}
