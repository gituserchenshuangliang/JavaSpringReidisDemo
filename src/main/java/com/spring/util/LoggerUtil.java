package com.spring.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @description:日志工具
 * @author: Cherry
 * @time: 2020/6/5 8:55
 */
public class LoggerUtil {
    public static Logger getLog(Class clazz){
        return LogManager.getLogger(clazz.getName());
    }
}
